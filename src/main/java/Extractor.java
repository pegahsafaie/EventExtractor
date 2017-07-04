import com.fasterxml.jackson.databind.ObjectMapper;
import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.parser.nndep.Classifier;
import org.lambda3.graphene.core.Graphene;
import org.lambda3.graphene.core.relation_extraction.model.ExContent;
import org.lambda3.graphene.core.relation_extraction.model.ExElement;
import org.lambda3.graphene.core.relation_extraction.model.ExSPO;
import org.lambda3.graphene.core.relation_extraction.model.ExVContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by pegah on 7/3/17.
 */
public class Extractor {

    private static final Logger LOG = LoggerFactory.getLogger(Extractor.class);
    static List<Event> events;
    static CRFClassifier<CoreLabel> classifier;

    public static void main(String[] args) throws IOException {

        //read input
        if (args.length < 1) {
            System.out.print("you should specify (1)raw file path as input argument, (2)address of propBank SRL");
            return;
        }
        events = new ArrayList<Event>();
        classifier = CRFClassifier.getDefaultClassifier();
        File file = new File(args[0]);
        FileReader reader = new FileReader(file);
        char[] chars = new char[(int) file.length()];
        reader.read(chars);
        String content = new String(chars);
        reader.close();

        //simplification with Graphene
        final Graphene graphene = new Graphene();
        ExContent ec = graphene.doRelationExtraction(content, false); // set true to enable coreference resolution

        for (ExElement element : ec.getElements()) {
            try {
                if (isEvent_NER(element)) {
                    Event event = new Event();
                    ExSPO eXSpo = element.getSpo().get();
                    event.setVerb(eXSpo.getPredicate());
                    event.setObject(eXSpo.getObject());
                    event.setPredicate(eXSpo.getPredicate());
                    event.setSubject(eXSpo.getSubject());
                    event.setSentence(element.getNotSimplifiedText());
                    String contexts = "";
                    for (ExVContext verbContext : element.getVContexts()) {
                        contexts += verbContext.getClassification().name() + "_";
                    }
                    event.setvContexts(contexts);
                    events.add(event);
                }
            } catch (Exception ex) {
                System.out.print("ERROR:" + ex.getMessage());
            }
        }
        convertEventsToJson();
    }

    private static boolean isEvent_Graphene(ExElement element) {
        boolean isEvent = false;
        List<ExVContext> verbContexts = element.getVContexts();
        for (ExVContext verbContext : verbContexts) {
            if (verbContext.getClassification().toString().equals("TEMPORAL") ||
                    verbContext.getClassification().toString().equals("SPATIAL")) {
                return true;
            }
        }
        return isEvent;
    }

    private static boolean isEvent_NER(ExElement element) {
        boolean isEvent = false;
        List<List<CoreLabel>> classify = classifier.classify(element.getNotSimplifiedText());
        for (List<CoreLabel> coreLabels : classify) {
            for (CoreLabel coreLabel : coreLabels) {
                String word = coreLabel.word();
                String category = coreLabel.get(CoreAnnotations.AnswerAnnotation.class);
                if(category.equals("LOCATION") || category.equals("DATE") || category.equals("TIME"))
                    return true;
            }
        }
        return isEvent;
    }

    private static void convertEventsToJson() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.writeValue(new File("events.json"), events);
        } catch (Exception ex) {
            System.out.print("ERROR: " + ex.getMessage());
        }
    }
}
