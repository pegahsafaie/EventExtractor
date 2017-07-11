import com.fasterxml.jackson.databind.ObjectMapper;
import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.parser.nndep.Classifier;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.time.TimeAnnotations;
import edu.stanford.nlp.time.TimeAnnotator;
import edu.stanford.nlp.time.TimeExpression;
import edu.stanford.nlp.util.CoreMap;
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
import java.util.Properties;

/**
 * Created by pegah on 7/3/17.
 */
public class Extractor {

    private static final Logger LOG = LoggerFactory.getLogger(Extractor.class);
    static List<Event> events;
    static CRFClassifier<CoreLabel> classifier;
    static Graphene graphene;
    static AnnotationPipeline pipeline;

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
        graphene = new Graphene();
        grapheneExtractor(content);
        convertEventsToJson();
    }

    private static void grapheneExtractor(String content) {
        ExContent ec = graphene.doRelationExtraction(content, false); // set true to enable coreference resolution

        Properties props = new Properties();
        pipeline = new AnnotationPipeline();
        pipeline.addAnnotator(new TokenizerAnnotator(false));
        pipeline.addAnnotator(new WordsToSentencesAnnotator(false));
        pipeline.addAnnotator(new POSTaggerAnnotator(false));
        pipeline.addAnnotator(new TimeAnnotator("sutime", props));

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
                    Context[] contexts = new Context[element.getVContexts().size()];
                    int i = 0;
                    for (ExVContext verbContext : element.getVContexts()) {
                        Context context = new Context();
                        context.setClassification(verbContext.getClassification().name());
                        context.setText(verbContext.getText());
                        context.setEventDateTime(dateNormilize(verbContext.getText()));
                        contexts[i++] = context;
                    }
                    event.setvContexts(contexts);
                    events.add(event);
                }
            } catch (Exception ex) {
                System.out.print("ERROR:" + ex.getMessage());
            }
        }
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

    private static String[] dateNormilize(String text) {
        Annotation annotation = new Annotation(text);
//        annotation.set(CoreAnnotations.DocDateAnnotation.class, "2013-07-14");
        pipeline.annotate(annotation);
        System.out.println(annotation.get(CoreAnnotations.TextAnnotation.class));
        List<CoreMap> timexAnnsAll = annotation.get(TimeAnnotations.TimexAnnotations.class);
        String[] dateTimes = new String[timexAnnsAll.size()];
        int i = 0;
        for (CoreMap cm : timexAnnsAll) {
            dateTimes[i++] = cm.get(TimeExpression.Annotation.class).getTemporal().toString();
        }
        return dateTimes;
    }

    private static boolean isEvent_NER(ExElement element) {
        boolean isEvent = false;
        List<List<CoreLabel>> classify = classifier.classify(element.getNotSimplifiedText());
        for (List<CoreLabel> coreLabels : classify) {
            for (CoreLabel coreLabel : coreLabels) {
                String word = coreLabel.word();
                String category = coreLabel.get(CoreAnnotations.AnswerAnnotation.class);
                if (category.equals("LOCATION") || category.equals("DATE") || category.equals("TIME"))
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
