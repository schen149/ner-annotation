package io.github.mayhewsw;
import edu.illinois.cs.cogcomp.core.datastructures.IntPair;
import edu.illinois.cs.cogcomp.core.datastructures.Pair;
import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.Constituent;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.View;
import edu.illinois.cs.cogcomp.core.io.LineIO;
import edu.illinois.cs.cogcomp.core.utilities.SerializationHelper;
import edu.illinois.cs.cogcomp.core.utilities.StringUtils;
import edu.illinois.cs.cogcomp.nlp.corpusreaders.CoNLLNerReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

/**
 * This contains the main logic of the whole thing.
 */
@SuppressWarnings("ALL")
@Controller
public class AnnotationController {

    private static Logger logger = LoggerFactory.getLogger(AnnotationController.class);

    
    private HashMap<String, String> folders;
    private List<String> labels;
    private Dictionary dict;
    private HashMap<String,String> foldertypes;
    private final String FOLDERTA = "ta";
    private final String FOLDERCONLL = "conll";
//    private UgDictionary ugd;

    /**
     * When this class is loaded, it reads a file called config/folders.txt. This is made up
     * of lines formatted as:
     *    name path
     * The name is an identifier, the path is the absolute path to the folder. This
     * folder path must contain TextAnnotations.
     *
     * @throws FileNotFoundException
     */
    public AnnotationController() throws IOException {

        logger.debug("Loading folders.txt");
        List<String> folderlines = LineIO.read("config/folders.txt");
        folders = new HashMap<String, String>();
        foldertypes = new HashMap<>();
        for(String line : folderlines){
            if(line.length() == 0 || line.startsWith("#")){
                continue;
            }
            String[] sl = line.trim().split("\\s+");
            logger.debug(line);
            logger.debug(sl.length + "");
            folders.put(sl[0], sl[1]);
            foldertypes.put(sl[0], sl[2]);
        }

        logger.debug("Loading labels.txt");
        List<String> labellines = LineIO.read("config/labels.txt");
        List<String> csslines = new ArrayList<String>();
        labels = new ArrayList<>();
        for(String line: labellines){
            if(line.length() == 0 || line.startsWith("#")){
                continue;
            }
            String[] sl = line.trim().split("\\s+");
            labels.add(sl[0]);
            csslines.add("." + sl[0] + "{ background-color: " + sl[1] + "; }");
        }
        logger.debug("using labels: " + labels.toString());

        LineIO.write("src/main/resources/static/css/labels.css", csslines);

        logger.debug("Loading dictionary.txt");
        List<String> lines = LineIO.read("config/dictionary.txt");
        // There should only be one line, and it should be the first line.
        String[] sl = lines.get(0).trim().split("\\s+");

        String lang = sl[0];
        String dictpath = sl[1];

        dict = new Dictionary(dictpath);

    }

    /**
     * Given a foldername (first field in folders.txt), this will get the path to that folder (second field
     * in folders.txt) and will read all textannotations from that folder.
     *
     * This returns a TreeMap from integer id to TextAnnotation. These ids are assigned only here -- they do
     * not correspond to the internal TextAnnotation id.
     *
     * @param folder folder identifier
     * @return
     * @throws IOException
     */
    public TreeMap<String, TextAnnotation> loadFolder(String folder, String username) throws Exception {

        String folderurl = folders.get(folder);
        String foldertype = foldertypes.get(folder);

        File f = new File(folderurl);

        // This will be ordered by it's keys.
        TreeMap<String, TextAnnotation> ret = new TreeMap<>(new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                int retval;
                try{
                    retval = Integer.compare(Integer.parseInt(o1), Integer.parseInt(o2));
                }catch(NumberFormatException e){
                    retval = o1.compareTo(o2);
                }
                return retval;
            }
        });

        if(foldertype.equals(FOLDERTA)) {
            String[] files = f.list();
            int limit = Math.min(files.length, 500);
            for (int i = 0; i < limit; i++) {
                String file = files[i];
                TextAnnotation ta = SerializationHelper.deserializeTextAnnotationFromFile(folderurl + "/" + file);
                ret.put(file, ta);
            }
        }else if(foldertype.equals(FOLDERCONLL)){
            CoNLLNerReader cnl = new CoNLLNerReader(folderurl);
            while(cnl.hasNext()){
                TextAnnotation ta = cnl.next();
                logger.info("Loading: " + ta.getId());
                ret.put(ta.getId(), ta);
            }
        }

        // now check the annotation folder to see what this user has already annotated.
        // if there is anything, load it here.
        String outfolder = folderurl.replaceAll("/$","") + "-annotation-" + username + "/";

        logger.info("Now looking in user annotation folder: " + outfolder);

        if((new File(outfolder)).exists()) {

            if (foldertype.equals(FOLDERTA)) {
                File outf = new File(outfolder);
                String[] files = outf.list();
                int limit = Math.min(files.length, 300);

                for (int i = 0; i < limit; i++) {
                    String file = files[i];
                    TextAnnotation ta = SerializationHelper.deserializeTextAnnotationFromFile(outfolder + "/" + file);
                    ret.put(file, ta);
                }
            } else if (foldertype.equals(FOLDERCONLL)) {
                CoNLLNerReader cnl = new CoNLLNerReader(outfolder);
                while (cnl.hasNext()) {
                    TextAnnotation ta = cnl.next();
                    logger.info("Loading: " + ta.getId());
                    ret.put(ta.getId(), ta);
                }
            }
        }

        return ret;
    }


    /**
     * This is called when the user clicks on the language button on the homepage.
     * @param folder
     * @param hs
     * @return
     * @throws IOException
     */
    @RequestMapping(value = "/loaddata", method=RequestMethod.GET)
    public String dummy(@RequestParam(value="folder") String folder, HttpSession hs) throws Exception {
        String username = (String) hs.getAttribute("username");
        TreeMap<String, TextAnnotation> tas = loadFolder(folder, username);
        hs.setAttribute("tas", tas);
        hs.setAttribute("dataname", folder);

        return "redirect:/annotation";
    }

    /**
     * This is called when the user clicks on the language button on the homepage.
     * @param folder
     * @param hs
     * @return
     * @throws IOException
     */
    @RequestMapping(value = "/addword", method=RequestMethod.GET)
    @ResponseStatus(value = HttpStatus.OK)
    public void addword(@RequestParam(value="key") String key, @RequestParam(value="def") String def, HttpSession hs) throws Exception {
        logger.info("Adding to dict: " + key + " -> " + def);
        this.dict.add(key, def);
    }

    @RequestMapping(value = "/save", method=RequestMethod.GET)
    public String save(@RequestParam(value="taid", required=true) String taid, HttpSession hs) throws IOException {

        // write out to
        String username = (String) hs.getAttribute("username");
        String folder = (String) hs.getAttribute("dataname");
        String folderpath = folders.get(folder);
        String foldertype = foldertypes.get(folder);

        if(username != null && folderpath != null) {

            folderpath = folderpath.replaceAll("/$", "");
            String outpath = folderpath + "-annotation-" + username + "/";
            logger.info("Writing out to: " + outpath);
            logger.info("id is: " + taid);

            TreeMap<String, TextAnnotation> tas = (TreeMap<String, TextAnnotation>) hs.getAttribute("tas");
            TextAnnotation taToSave = tas.get(taid);
            String savepath = outpath + taid;

            if(foldertype.equals(FOLDERTA)) {
                SerializationHelper.serializeTextAnnotationToFile(taToSave, savepath, true);
            }else if(foldertype.equals(FOLDERCONLL)) {
                CoNLLNerReader.TaToConll(Collections.singletonList(taToSave), outpath);
            }

        }
        // nothing happens to this...
        return "redirect:/";
    }

    @RequestMapping("/")
    public String home(Model model){
        model.addAttribute("folders", folders.keySet());
        model.addAttribute("user", new User());
        return "home";
    }

    @RequestMapping(value="/setname")
    public String setname(@ModelAttribute User user, HttpSession hs){
        logger.info("Setting name to: " + user.getName());
        // Just make sure everything is clear first... just in case.
        logger.info("Logging in!");
        hs.removeAttribute("username");
        hs.removeAttribute("dataname");
        hs.removeAttribute("tas");

        hs.setAttribute("username", user.getName());
        return "redirect:/";
    }

    @RequestMapping(value="/logout")
    public String logout(HttpSession hs){
        logger.info("Logging out...");
        hs.removeAttribute("username");
        hs.removeAttribute("dataname");
        hs.removeAttribute("tas");
        return "redirect:/";
    }

    @RequestMapping(value="/annotation", method=RequestMethod.GET)
    public String annotation(@RequestParam(value="taid", required=false) String taid, HttpSession hs, Model model, RedirectAttributes redirectAttributes) {

        TreeMap<String, TextAnnotation> tas = (TreeMap<String, TextAnnotation>) hs.getAttribute("tas");

        // Go to the homepage.
        if(tas == null){
            return "redirect:/";
        }

        // If there's no taid, then return the getstarted page (not a redirect).
        if(taid == null){
            return "getstarted";
        }

        if(!tas.containsKey(taid)){
            return "redirect:/annotation";
        }

        TextAnnotation ta = tas.get(taid);
        View ner = ta.getView(ViewNames.NER_CONLL);
        View sents = ta.getView(ViewNames.SENTENCE);

        model.addAttribute("ta", ta);
        model.addAttribute("taid", taid);

        logger.info(String.format("Viewing TextAnnotation (id=%s)", taid));
        logger.info("Text (trunc): " + ta.getTokenizedText().substring(0, Math.min(20, ta.getTokenizedText().length())));
        logger.info("Num Constituents: " + ner.getConstituents().size());
        logger.info("Constituents: " + ner.getConstituents());

        String[] text = ta.getTokenizedText().split(" ");

        // add spans to every word that is not a constituent.
        for(int t = 0; t < text.length; t++){
            String def = null;
            if(dict.containsKey(text[t])){
                def = dict.get(text[t]).get(0);
            }
            text[t] = "<span class='token pointer' def='<i>"+ def +"</i>' id='tok-" + t + "'>" + text[t] + "</span>";
        }

        for(Constituent c : ner.getConstituents()){

            int start = c.getStartSpan();
            int end = c.getEndSpan();

            // important to also include 'cons' class, as it is a keyword in the html
            text[start] = String.format("<span class='%s pointer cons' id='cons-%d-%d'>%s", c.getLabel(), start, end, text[start]);
            text[end-1] += "</span>";

        }

        for(Constituent c : sents.getConstituents()){

            int start = c.getStartSpan();
            int end = c.getEndSpan();
            text[end-1] += "<br />";
        }


        String out = StringUtils.join(" ", text);

        model.addAttribute("htmlstring", out);

        if(!tas.firstKey().equals(taid)) {
            model.addAttribute("previd", tas.lowerKey(taid));
        }else{
            model.addAttribute("previd", -1);
        }

        if(!tas.lastKey().equals(taid)) {
            model.addAttribute("nextid", tas.higherKey(taid));
        }else{
            model.addAttribute("nextid", -1);
        }

        model.addAttribute("labels", labels);

        return "annotation";
    }

    /**
     * This is called when the label button is clicked for a word.
     * This should never get label O
     * @param label
     * @param spanid
     * @param idstring
     * @param hs
     * @param model
     * @return
     * @throws Exception
     */
    @RequestMapping(value="/addtoken", method=RequestMethod.POST, produces = "application/text")
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public String addtoken(@RequestParam(value="label") String label, @RequestParam(value="spanid") String spanid, @RequestParam(value="id") String idstring, HttpSession hs, Model model) throws Exception {

        logger.info(String.format("TextAnnotation with id %s: change span (id:%s) to label: %s.", idstring, spanid, label));

        String[] ss = spanid.split("-");
        IntPair span = new IntPair(Integer.parseInt(ss[1]), Integer.parseInt(ss[2]));

        TreeMap<String, TextAnnotation> tas = (TreeMap<String, TextAnnotation>) hs.getAttribute("tas");

        TextAnnotation ta = tas.get(idstring);
        String[] spantoks = ta.getTokensInSpan(span.getFirst(), span.getSecond());

        String text = StringUtils.join(" ", spantoks);
        logger.info(text);
        logger.info(spanid);

        View ner = ta.getView(ViewNames.NER_CONLL);
        List<Constituent> lc = ner.getConstituentsCoveringSpan(span.getFirst(), span.getSecond());

        int origstart = span.getFirst();
        int origend = span.getSecond();
        String origlabel = null;
        if(lc.size() > 0) {
            Constituent oldc = lc.get(0);
            ner.removeConstituent(oldc);
        }

        // an O label means don't add the constituent.
        if(label.equals("O")) {
            System.err.println("Should never happen: label is O");
        }else{
            Constituent newc = new Constituent(label, ViewNames.NER_CONLL, ta, span.getFirst(), span.getSecond());
            ner.addConstituent(newc);
        }

        List<IntPair> matchingspans = new ArrayList<>();

        // only return spans that are not labeled, and which are not the current span.
        // Without this, there would be an infinite loop of labeling.
        for(IntPair sp : ta.getSpansMatching(text)){
            if(sp == span){
                continue;
            }
            List<Constituent> conscovering = ner.getConstituentsCoveringSpan(sp.getFirst(), sp.getSecond());
            if(conscovering.size() == 0) {
                matchingspans.add(sp);
            }
        }

        logger.info("Matching (unlabeled) spans: " + matchingspans);


        return matchingspans.toString().replaceAll("\\)", "]").replaceAll("\\(", "[");

    }

    @RequestMapping(value="/removetoken", method=RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    public void removetoken(@RequestParam(value="tokid") String tokid,  @RequestParam(value="id") String idstring, HttpSession hs, Model model) throws Exception {

        logger.info(String.format("TextAnnotation with id %s: remove token (id:%s).", idstring, tokid));

        String[] ss = tokid.split("-");
        int inttokid = Integer.parseInt(ss[1]);
        Pair<Integer, Integer> tokspan = new Pair<>(inttokid, inttokid+1);

        TreeMap<String, TextAnnotation> tas = (TreeMap<String, TextAnnotation>) hs.getAttribute("tas");

        TextAnnotation ta = tas.get(idstring);

        String[] spantoks = ta.getTokensInSpan(tokspan.getFirst(), tokspan.getSecond());
        String text = StringUtils.join(" ", spantoks);
        logger.info(text);

        View ner = ta.getView(ViewNames.NER_CONLL);
        List<Constituent> lc = ner.getConstituentsCoveringSpan(tokspan.getFirst(), tokspan.getSecond());

        if(lc.size() > 0) {
            Constituent oldc = lc.get(0);

            int origstart = oldc.getStartSpan();
            int origend = oldc.getEndSpan();
            String origlabel = oldc.getLabel();
            ner.removeConstituent(oldc);

            if(origstart != tokspan.getFirst()){
                // this means last token is being changed.
                Constituent newc = new Constituent(origlabel, ViewNames.NER_CONLL, ta, origstart, tokspan.getFirst());
                ner.addConstituent(newc);
            }else if(origend != tokspan.getSecond()){
                // this means first token is being changed.
                Constituent newc = new Constituent(origlabel, ViewNames.NER_CONLL, ta, tokspan.getSecond(), origend);
                ner.addConstituent(newc);
            }
        }
    }

}
