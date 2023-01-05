import com.junodx.api.models.auth.User;
import com.junodx.api.models.laboratory.TestReport;
import com.junodx.api.models.laboratory.reports.NIPSReportStrings;
import com.junodx.api.models.laboratory.reports.RawData;
import com.junodx.api.models.laboratory.reports.nipsstrings.Condition;
import com.lowagie.text.DocumentException;
import de.neuland.pug4j.Pug4J;
import de.neuland.pug4j.PugConfiguration;
import de.neuland.pug4j.template.PugTemplate;

import java.io.*;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

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

            if (report == null)
                return;

            NIPSReportStrings strings = (NIPSReportStrings) report.getResultData().getStrings();

            User patient = report.getPatient();
            if (patient == null) {
                System.err.println("Cannot find patient details in test report");
                return;
            }

            RawData status = report.getResultData().getData();
            JSONObject statusJson = new JSONObject(status);
            Date signedOutAt = report.getSignoutDetails().getSignedOutAt().getTime();
            Date lmp = patient.getPatientDetails().getMedicalDetails().getLmpDate().getTime();
            Date collection = null;
            if (report.getSampleCollectionTimestamp() != null)
                collection = report.getSampleCollectionTimestamp().getTime();

            SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd");
            Date date = new Date();
            String signDate = sdf2.format(date);
            String resultsForText = StringUtils.capitalize(statusJson.get("overallCondition").toString().toLowerCase())
                    + " Result for " + strings.getTestDetails().getTestShortName();
            String reportType = report.getReportConfiguration().toString();

            Map<String, Object> obj = new HashMap<>();

            Integer numberOfFetuses = patient.getPatientDetails().getMedicalDetails().getNumberOfFetuses();
            System.out.println(patient.getPatientDetails().getMedicalDetails().getNumberOfFetuses());
            obj.put("numberOfFetuses", numberOfFetuses);
            obj.put("withSignature", true);
            obj.put("signDate", signDate);
            if (strings.getSignatureDetails().getSignatureAssetUrl() != null)
                obj.put("signatureLink", strings.getSignatureDetails().getSignatureAssetUrl());

            obj.put("overallCondition", statusJson.get("overallCondition").toString());
            obj.put("patientName", patient.getFirstName() + " " + patient.getLastName());
            obj.put("patientDob", patient.getDateOfBirth());
            obj.put("patientEmail", patient.getEmail());
            if (lmp != null)
                obj.put("patientLmpDate", sdf2.format(lmp));
            if (collection != null)
                obj.put("patientSampleCollectionDate", collection != null ? sdf2.format(collection) : "");
            obj.put("patientGA", report.getPatientGAAtCollection());
            // obj.put("patientGestationType", "Singleton");
            if (signedOutAt != null)
                obj.put("reportDate", sdf2.format(signedOutAt));
            else {
                if (report.getReportDateTime() != null)
                    obj.put("reportDate", sdf2.format(report.getReportDateTime().getTime()));
                else
                    obj.put("reportDate", sdf2.format(Calendar.getInstance().getTime()));
            }

            obj.put("resultsForText", resultsForText);
            obj.put("testNameShort", strings.getTestDetails().getTestShortName());
            obj.put("testNameExtended", strings.getTestDetails().getTestName());
            obj.put("testName", strings.getTestDetails().getTestName());
            obj.put("testDescription", strings.getTestDetails().getTestDescription());

            obj.put("overallResultText", strings.getOverallResults().getOverallResultText());
            obj.put("overallResultSVGPdf", strings.getOverallResults().getOverallResultSVGPdf());
            obj.put("fetalSexText", strings.getOverallResults().getOverallResultFetalSexText());
            obj.put("overallFetalSexSVGPdf", strings.getOverallResults().getOverallFetalSexSVGPdf());
            obj.put("fetalFractionText", strings.getOverallResults().getOverallResultFetalFractionText());
            obj.put("overallFetalFractionSVGPdf", strings.getOverallResults().getOverallFetalFractionSVGPdf());

            obj.put("overallResultsSummary", strings.getOverallResults().getOverallResultsSummary());
            obj.put("overallResult", status);

            // PPV Section
            obj.put("ppvPercentage", strings.getPpvDetails().getPpvPercent());
            obj.put("ppvPatientAge", strings.getPpvDetails().getPpvPatientAge());
            obj.put("ppvPatientGA", strings.getPpvDetails().getPpvPatientGA());
            obj.put("ppvExplanation", strings.getPpvDetails().getPpvExplanation());
            obj.put("labNotes", report.getNotes());

            // Conditions section
            int count = 1;
            for (Condition condition : strings.getConditions()) {
                obj.put("condition" + count + "Title", condition.getLabel());
                obj.put("condition" + count + "Risk", condition.getText());
                obj.put("condition" + count + "InterpretationText", condition.getDetectionString());
                obj.put("condition" + count + "Icon", condition.getIcon());
                count++;
            }

            if (obj.containsKey("condition4Icon")) {
                String[] scaIconLinkArray = obj.get("condition4Icon").toString().split("/");
                int scaChromoLength = scaIconLinkArray[scaIconLinkArray.length - 1].split("\\.")[0].length();
                obj.put("SCAIcon", scaChromoLength);
            }

            // obj.put("testNameExtended", "ABOUT JUNO'S HAZEL™ NON - INVASIVE PRENATAL
            // SCREEN:"); //to-do
            obj.put("patientDemographics", "PATIENT DEMOGRAPHICS");

            // Table Headers
            obj.put("CONDITIONS_EVALUATED_HEADER", "CONDITIONS EVALUATED");
            obj.put("FINAL_TEST_SUMMARY_HEADER", "FINAL RESULTS SUMMARY");
            obj.put("WHAT_DOES_THIS_RESULT_MEAN_HEADER", "WHAT DOES THIS RESULT MEAN?");
            obj.put("SCREENING_METHODS_HEADER", "SCREENING METHODS");
            obj.put("SCREENING_PERFORMANCE_HEADER", "SCREENING PERFORMANCE");
            obj.put("SCREENING_LIMITATIONS_HEADER", "SCREENING LIMITATIONS");
            obj.put("SCREENING_REFERENCES_HEADER", "REFERENCES");
            obj.put("LAB_DIRECTOR_COMMENTS", "LAB DIRECTOR COMMENTS");

            Boolean t13Call = statusJson.getJSONObject("t13").get("call").toString() == "POSITIVE";
            Boolean t18Call = statusJson.getJSONObject("t18").get("call").toString() == "POSITIVE";
            Boolean t21Call = statusJson.getJSONObject("t21").get("call").toString() == "POSITIVE";

            Boolean scaCall = false;
            if (reportType == "NIPS_PLUS") {
                scaCall = statusJson.getJSONObject("sca").get("scaCall").toString() == "POSITIVE";
            }

            String ppvHeader = t13Call || t21Call || t18Call ? "POSITIVE PREDICTIVE VALUE"
                    : scaCall ? "SEX CHROMOSOME ANEUPLOIDY RISK ASSESSMENT" : "POSITIVE PREDICTIVE VALUE";
            obj.put("POSITIVE_PREDICTIVE_VALUE_HEADER", ppvHeader);

            // table rows static text
            obj.put("RESULT", "Result");
            obj.put("FETAL_SEX", "Fetal Sex");
            obj.put("FETAL_FRACTION", "Fetal Fraction");
            obj.put("POST_TEST_RISK", "Post-test risk");
            obj.put("INTERPRETATION", "Interpretation");

            /*
             * Results meaning section
             * obj.put("resultsMeaning",
             * strings.getOverallResults().getOverallResultsSummary());
             */

            // Static text section
            obj.put("screeningPerformaceText", strings.getStaticText().getScreeningPerformance());
            obj.put("screeningMethodText", strings.getStaticText().getScreeningMethods());
            obj.put("disclaimer", strings.getStaticText().getDisclaimer().replaceAll("’", "'"));
            obj.put("hipaaDisclaimer", strings.getStaticText().getHipaaDisclaimer());

            String junoFooter = strings.getStaticText().getJunoFooter();
            String junoLabAddress = junoFooter.substring(junoFooter.indexOf(":") + 1, junoFooter.indexOf(";") + 1);
            String cliaId = junoFooter.substring(junoFooter.indexOf(":", junoFooter.indexOf(junoLabAddress) + 1) + 1,
                    junoFooter.indexOf(";", junoFooter.indexOf(junoLabAddress) + junoFooter.indexOf(";")) + 1);
            // String junoLabDirector = junoFooter.substring(junoFooter.indexOf(":",
            // junoFooter.indexOf(cliaId) + 1) + 1);

            obj.put("junoLabAddress", junoLabAddress);
            obj.put("cliaId", cliaId);
            obj.put("junoLabDirector", strings.getSignatureDetails().getSignatoryName());

            PugConfiguration config = new PugConfiguration();
            config.setMode(Pug4J.Mode.HTML);
            PugTemplate template = Pug4J.getTemplate("./templates/report.pug");
            if (reportType == "NIPS_PLUS") {
                template = Pug4J.getTemplate("./templates/nips_plus_report.pug");
            }

            String html = Pug4J.render(template, obj);

            String screeningLimitations = strings.getStaticText().getScreeningLimitations().replaceAll("<br>\\\n", "");
            html = html.replace("[screeningLimitations]", screeningLimitations);

            System.out.println("HTML: " + mapper.writeValueAsString(html));

            HtmlConverter.convertToPdf(html,
                    new FileOutputStream(obj.get("patientName") + "_" + report.getId() + ".pdf"));
        } catch (URISyntaxException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // this.fileUrl = awsClient.uploadFile(obj.get("patientName")+".pdf");
    }

    public static UrlClientConnection getJunoConnectionFromConfiguration() {
        Properties props = new Properties();
        InputStream inStream;
        String applicationConfigurationFileName = "application.properties";

        UrlClientConnection connection = new UrlClientConnection();
        try {
            inStream = SimplePDFWriterTestMain.class.getClassLoader()
                    .getResourceAsStream(applicationConfigurationFileName);
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
