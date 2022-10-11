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

            /*
            PPV Section

            obj.put("ppvPercentage", strings.getPpvDetails().getPpvPercent());
            obj.put("ppvPatientAge", strings.getPpvDetails().getPpvPatientAge());
            obj.put("ppvPatientGA", strings.getPpvDetails().getPpvPatientGA());
            obj.put("ppvExplanation", strings.getPpvDetails().getPpvExplanation());
             */

            /*
            Conditions section
            int count = 1;
            for(Condition condition : strings.getConditions()) {
                obj.put("condition" + count, condition.getLabel());
                obj.put("condition" + count, condition.getText());
                obj.put("condition" + count, condition.getDetectionString());
                obj.put("condition" + count, condition.getIcon());
                count++;
                //The fetal sex is included in the conditions list, need to ignore that explicitly for this report
            }
             */

            /*
             * Results meaning section
             * obj.put("resultsMeaning", strings.getOverallResults().getOverallResultsSummary());
             */

            /*
             * Static text section
             *
            obj.put("screeningPerformance", strings.getStaticText().getScreeningPerformance());
            obj.put("screeningLimitations", strings.getStaticText().getScreeningLimitations());
            obj.put("screeningReferences", strings.getStaticText().getReferences());
            obj.put("disclaimer", strings.getStaticText().getDisclaimer());
            obj.put("hipaaDisclaimer", strings.getStaticText().getHipaaDisclaimer());
            obj.put("junoFooter", strings.getStaticText().getJunoFooter());

             */

            PugConfiguration config = new PugConfiguration();
            config.setMode(Pug4J.Mode.HTML);
            PugTemplate template = Pug4J.getTemplate("./templates/report.pug");

            String html = Pug4J.render(template, obj);

            System.out.println("HTML: " + mapper.writeValueAsString(html));

            // alternate pdf converter, for checking which PDF converter is suitable for our reports
            // HtmlConverter.convertToPdf(html, new FileOutputStream("string-to-pdf.pdf"));

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(html);
            renderer.layout();
            renderer.createPDF(outputStream, false);
            renderer.finishPDF();

            ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
            IOUtils.copy(inputStream, new FileOutputStream(obj.get("patientName") + ".pdf"));

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
