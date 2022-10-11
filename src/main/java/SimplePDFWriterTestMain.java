import com.junodx.api.models.auth.User;
import com.junodx.api.models.laboratory.TestReport;
import com.junodx.api.models.laboratory.reports.NIPSReportStrings;
import com.junodx.api.models.laboratory.reports.nipsstrings.Condition;
import com.lowagie.text.DocumentException;
import de.neuland.pug4j.Pug4J;
import de.neuland.pug4j.PugConfiguration;
import de.neuland.pug4j.template.PugTemplate;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.*;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.html2pdf.HtmlConverter;

public class SimplePDFWriterTestMain {


    public static void main(String[] args) throws DocumentException, IOException {
        System.out.println("Your Command Line arguments are:"); // loop through all arguments and print it to the user
        for (String str : args) {
            System.out.println(str);
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm");

        ObjectMapper mapper = new ObjectMapper();
        JunoService junoService = new JunoService(getJunoConnectionFromConfiguration(), null);
        try {
            TestReport report = junoService.getTestReportForPatient(args[0]);

            if(report == null)
                return;

      System.out.println("Report: " + mapper.writeValueAsString(report));
            NIPSReportStrings strings = (NIPSReportStrings) report.getResultData().getStrings();
            User patient = report.getPatient();
            if(patient == null) {
                System.err.println("Cannot find patient details in test report");
                return;
            }

            Date signedOutAt = report.getSignoutDetails().getSignedOutAt().getTime();
            Date lmp = patient.getPatientDetails().getMedicalDetails().getLmpDate().getTime();
            Date collection = null;
            if(report.getSampleCollectionTimestamp() != null)
                collection = report.getSampleCollectionTimestamp().getTime();

            Map<String, Object> obj = new HashMap<>();
            obj.put("patientName", patient.getFirstName() + " " + patient.getLastName());
            obj.put("patientDob", patient.getDateOfBirth());
            obj.put("patientEmail", patient.getEmail());
            obj.put("reportDate", sdf.format(signedOutAt));
            obj.put("productName", "Positive Result for Hazel NIPS Basic");
            obj.put("testNameShort", strings.getTestDetails().getTestShortName());
            obj.put("patientLmpDate", sdf.format(lmp));
            obj.put("patientSampleCollectionDate", collection != null ? sdf.format(collection) : "");
            obj.put("patientGA", strings.getPpvDetails().getPpvPatientGA());
            obj.put("patientGestationType", "Singleton");
            obj.put("testNameExtended", strings.getTestDetails().getTestName());
            obj.put("testDescription", strings.getTestDetails().getTestDescription());
            obj.put("overallResultText", strings.getOverallResults().getOverallResultText());
            obj.put("overallResultGraphic", strings.getOverallResults().getOverallResultSVGPdf());
            obj.put("fetalSexText", strings.getOverallResults().getOverallResultFetalSexText());
            obj.put("overallSexGraphic", strings.getOverallResults().getOverallFetalSexSVGPdf());
            obj.put("fetalFractionText", strings.getOverallResults().getOverallResultFetalFractionText());
            obj.put("fetalFractionGraphic", strings.getOverallResults().getOverallFetalFractionSVGPdf());
            
            obj.put("patientDemographics", "PATIENT DEMOGRAPHICS");
            obj.put("testNameExtended", "ABOUT JUNO'S HAZEL™ NON - INVASIVE PRENATAL SCREEN:");
            obj.put("testDescription", "Juno Diagnostics' Hazel™ laboratory-developed test (LDT) is a screening evaluation which analyzes circulating cell-free DNA from a maternal blood sample for chromosomal and subchromosomal representations of the fetus and gestational carrier. The screen is indicated for use in human pregnancies for the screening of fetal chromosomal aberrations. Validation data on multiple pregnancies, such as twins, is limited and the ability of this screen to detect aneuploidy in a triplet pregnancy has not yet been validated.");

            obj.put("FINAL_TEST_SUMMARY_HEADER", "FINAL RESULTS SUMMARY");
            obj.put("RESULT", "Result");
            obj.put("FETAL_SEX", "Fetal Sex");
            obj.put("FETAL_FRACTION", "Fetal Fraction");

            obj.put("POSITIVE_PREDICTIVE_VALUE_HEADER", "POSITIVE PREDICTIVE VALUE");
            // obj.put("predictivePositiveValue", strings.getPpvDetails().getPpvPercent());
            obj.put("RESULT", "Result");
            
            // PPV Section
            obj.put("ppvPercentage", strings.getPpvDetails().getPpvPercent());
            obj.put("ppvPatientAge", strings.getPpvDetails().getPpvPatientAge());
            obj.put("ppvPatientGA", strings.getPpvDetails().getPpvPatientGA());
            obj.put("ppvExplanation", strings.getPpvDetails().getPpvExplanation());
            

            
            obj.put("POST_TEST_RISK", "Post-test risk");
            obj.put("INTERPRETATION", "Interpretation");


            obj.put("WHAT_DOES_THIS_RESULT_MEAN_HEADER", "WHAT DOES THIS RESULT MEAN?");
            obj.put("resultMeaningText", "Results are consistent with a female fetus at increased risk for trisomy 21 (Down syndrome). Approximately 9 out of every 10 people with this result will have a baby with Down syndrome. Genetic counseling and prenatal diagnostic testing is recommended.");
            
            obj.put("SCREENING_METHODS_HEADER", "SCREENING METHODS");
            obj.put("screeningMethodTest", "Circulating cell-free DNA (ccfDNA) is purified from the plasma component of maternal blood. The extracted DNA is then converted into a whole genome DNA library for sequencing-based analysis of chromosomes 21, 18, and 13.");
            obj.put("SCREENING_PERFORMANCE_HEADER", "SCREENING PERFORMANCE");
            obj.put("screeningPerformaceText", "Juno’s Hazel™ laboratory developed test has been evaluated for clinical performance in multiple studies, inclusive of >1600 total venous and capillary samples. Based on this data, expected performance with at-home selfcollection is as follows:");


            obj.put("SCREENING_LIMITATIONS_HEADER", "SCREENING LIMITATIONS");
            obj.put("screeningLimitationsText1", "This screen is for screening purposes only, and is not diagnostic. While the results of these screens are highly accurate, discordant results, including inaccurate fetal sex prediction, may occur due to placental, maternal, or fetal mosaicism or neoplasm; vanishing twin; prior maternal organ transplant; or other causes. Sex chromosomal aneuploidies are not reportable for known multiple gestations. ");
            obj.put("screeningLimitationsText2", "The screen does not replace the accuracy and precision of prenatal diagnosis with CVS or amniocentesis. A patient with a positive screening result should receive genetic counseling and be offered invasive prenatal diagnosis for confirmation of test results. A negative result does not ensure an unaffected pregnancy nor does it exclude the possibility of other chromosomal abnormalities or birth defects which are not a part of this screening evaluation. An uninformative result may be reported, the causes of which may include but are not limited to insufficient sequencing coverage, noise or artifacts in the region, amplification or sequencing bias, or insufficient fetal representation. The ability to report results may be impacted by maternal BMI, maternal weight and maternal autoimmune disorders. ");
            obj.put("screeningLimitationsText3", "Screening for whole chromosome abnormalities (including sex chromosomes) and for sub-chromosomal abnormalities could lead to the potential discovery of both fetal and maternal genomic abnormalities that could have major, minor, or no, clinical significance. Evaluating the significance of a positive or a non-reportable result may involve both diagnostic testing and additional studies on the pregnant person. Such investigations may lead to a diagnosis of maternal chromosomal or sub-chromosomal abnormalities, which on occasion may be associated with benign or malignant maternal neoplasms. ");
            obj.put("screeningLimitationsText4", "This screen may not accurately identify fetal triploidy, balanced rearrangements, or the precise location of sub-chromosomal duplications or deletions; these may be detected by prenatal diagnosis with karyotype and SNP-microarray. Cell-free DNA screening is not intended to identify pregnancies at risk for neural tube defects or ventral wall defects; these may be detected by prenatal ultrasound evaluation.");
            obj.put("screeningLimitationsText5", "The results of this screening, including the benefits and limitations, should be discussed with a qualified healthcare provider. Pregnancy management decisions, including termination of the pregnancy, should not be based on the results of these screens alone. The healthcare provider is responsible for the use of this information in the management of their patient.");
            // Conditions section

            int count = 1;
            for(Condition condition : strings.getConditions()) {
                obj.put("condition" + count + "Title", condition.getLabel());
                obj.put("condition" + count + "Risk", condition.getText());
                obj.put("condition" + count + "InterpretationText", condition.getDetectionString());
                obj.put("condition" + count + "Icon", condition.getIcon());
                count++;
                //The fetal sex is included in the conditions list, need to ignore that explicitly for this report
            }
                 /* 

            /*
             * Results meaning section
             * obj.put("resultsMeaning", strings.getOverallResults().getOverallResultsSummary());
               /*

            /*
             * Static text section
             */
            obj.put("screeningPerformance", strings.getStaticText().getScreeningPerformance());
            obj.put("screeningLimitations", strings.getStaticText().getScreeningLimitations());
            obj.put("screeningReferences", "REFERENCES" );
            obj.put("disclaimer", strings.getStaticText().getDisclaimer());
            obj.put("hipaaDisclaimer", strings.getStaticText().getHipaaDisclaimer());
            obj.put("junoFooter", strings.getStaticText().getJunoFooter());
            obj.put("testName", "JUNO: HAZEL™ NON-INVASIVE PRENATAL SCREEN BASIC");


            PugConfiguration config = new PugConfiguration();
            config.setMode(Pug4J.Mode.HTML);
            PugTemplate template = Pug4J.getTemplate("./templates/report.pug");

            String html = Pug4J.render(template, obj);

            System.out.println("HTML: " + mapper.writeValueAsString(html));

            // alternate pdf converter, for checking which PDF converter is suitable for our reports
            HtmlConverter.convertToPdf(html, new FileOutputStream(obj.get("patientName") + ".pdf"));

            // ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            // ITextRenderer renderer = new ITextRenderer();
            // renderer.setDocumentFromString(html);
            // renderer.layout();
            // renderer.createPDF(outputStream, false);
            // renderer.finishPDF();

            // ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
            // IOUtils.copy(inputStream, new FileOutputStream(obj.get("patientName") + ".pdf"));

        } catch (URISyntaxException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        //this.fileUrl = awsClient.uploadFile(obj.get("patientName")+".pdf");
    }

    public static UrlClientConnection getJunoConnectionFromConfiguration() {
        Properties props = new Properties();;
        InputStream inStream;
        String applicationConfigurationFileName = "application.properties";

        UrlClientConnection connection = new UrlClientConnection();
        try {
            inStream = SimplePDFWriterTestMain.class.getClassLoader().getResourceAsStream(applicationConfigurationFileName);
            if (inStream != null) {
                props.load(inStream);

                connection.setClientId(props.getProperty("clientId"));
                connection.setClientSecret(props.getProperty("clientSecret"));
                connection.setUrl(props.getProperty("baseUrl"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return connection;
    }
}
