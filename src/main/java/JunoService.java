import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.junodx.api.controllers.lab.actions.TestReportUpdateActions;
import com.junodx.api.controllers.payloads.ClientCredentialsPayload;
import com.junodx.api.controllers.payloads.OAuth2TokenDTO;
import com.junodx.api.controllers.payloads.ReportConfigurationPayload;
import com.junodx.api.controllers.payloads.TestReportUpdateRequest;
import com.junodx.api.models.auth.User;
import com.junodx.api.models.laboratory.*;
import com.junodx.api.models.laboratory.reports.*;
import com.junodx.api.models.laboratory.tests.TestQC;
import com.junodx.api.models.laboratory.types.ReportConfiguration;
import com.junodx.api.models.laboratory.types.ReportType;
import com.junodx.api.services.exceptions.JdxServiceException;
import com.junodx.api.services.lab.TestReportService;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static java.lang.String.valueOf;

public class JunoService {
    UrlClientConnection connection;
    private OAuth2TokenDTO accessToken;

    ObjectMapper mapper;
    LambdaLogger logger;

    public JunoService(String url, String clientId, String clientSecret, LambdaLogger logger) {
        this.logger = logger;
        mapper = new ObjectMapper();

        this.connection = new UrlClientConnection();
        this.connection.setUrl(url);
        this.connection.setClientSecret(clientSecret);
        this.connection.setClientId(clientId);

        try {
            getAccessToken();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public JunoService(UrlClientConnection connection, LambdaLogger logger) {
        this.logger = logger;
        mapper = new ObjectMapper();

        this.connection = connection;

        try {
            getAccessToken();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    protected void getAccessToken() throws IOException, URISyntaxException, InterruptedException, HttpClientAccessException {
        ClientCredentialsPayload creds = new ClientCredentialsPayload();
        creds.setClientId(this.connection.getClientId());
        creds.setClientSecret(this.connection.getClientSecret());

        String tokenPayload = httpSend(this.connection.getUrl() + "/oauth2/token", "", mapper.writeValueAsString(creds));
        System.out.println("URL is " + this.connection.getUrl() + "/oauth2/token" + " Payload is " + tokenPayload);

        OAuth2TokenDTO token = mapper.readValue(tokenPayload, OAuth2TokenDTO.class);

        if(token != null)
            this.accessToken = token;
    }


    public String httpSend(String uri, String accessToken, Object body) throws URISyntaxException, IOException, InterruptedException {
        HttpRequest httpRequest;
        if(body != null) {
            String bodyString = "";
            if(body instanceof String)
                bodyString = body.toString();
            else
                bodyString = mapper.writeValueAsString(body);

            System.err.println("Body: " + bodyString);

            httpRequest = HttpRequest.newBuilder()
                    .uri(new URI(uri))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(bodyString))
                    .build();
        } else {
            httpRequest = HttpRequest.newBuilder()
                    .uri(new URI(uri))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();
        }

        HttpResponse<String> response = HttpClient
                .newBuilder()
                .build()
                .send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if(response.statusCode() == 200)
            return response.body();
        else
            throw new HttpClientAccessException("Error occurred calling Juno API " + response.statusCode() + " : " + response.body() + " ");
    }

    public TestReport getTestReportForPatient(String reportId) throws URISyntaxException, IOException, InterruptedException {
        String queryString = reportId + "?target=PROVIDER&withstrings=true";
        String reportQueryPayload = httpSend(this.connection.getUrl() + "/labs/reports/" + queryString, accessToken.getAccessToken(), null);
        try {
            return JunoService.customJsonParserForTestReport(reportQueryPayload);
        } catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }

    public static TestReport customJsonParserForTestReport(String string) throws JdxServiceException {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(string);
            return JunoService.customJsonParserFroTestReportFromNode(node);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static TestReport customJsonParserFroTestReportFromNode(JsonNode node) throws JdxServiceException, ParseException {
        ObjectMapper mapper = new ObjectMapper();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
        try {
            if (!node.isArray()) {
                TestReport report = new TestReport();
                if (node.has("reportConfiguration")) {
                    report.setReportConfiguration(ReportConfiguration.valueOf(node.get("reportConfiguration").textValue()));
                    String estAvailAt = node.get("estimatedToBeAvailableAt").textValue();
                    if (estAvailAt != null) {
                        Date date = sdf.parse(estAvailAt);
                        Calendar cal = Calendar.getInstance();
                        cal.setTime(date);
                        report.setEstimatedToBeAvailableAt(cal);
                    }
                }
                if (node.has("sampleCollectionTimestamp")) {
                    String collectionTimestamp = node.get("sampleCollectionTimestamp").textValue();
                    if (collectionTimestamp != null) {
                        Date date = sdf.parse(collectionTimestamp);
                        Calendar cal = Calendar.getInstance();
                        cal.setTime(date);
                        report.setSampleCollectionTimestamp(cal);
                    }
                }
                if (node.has("firstAvailableAt")) {
                    String firstAvailAt = node.get("firstAvailableAt").textValue();
                    if (firstAvailAt != null) {
                        Date date = sdf.parse(firstAvailAt);
                        Calendar cal = Calendar.getInstance();
                        cal.setTime(date);
                        report.setFirstAvailableAt(cal);
                    }
                }
                if (node.has("signoutDetails"))
                    report.setSignoutDetails(mapper.readValue(node.get("signoutDetails").toString(), Signout.class));
                if (node.has("patient"))
                    report.setPatient(mapper.readValue(node.get("patient").toString(), User.class));

                if (node.has("patientGAAtCollectionTimestamp"))
                    report.setPatientGAAtCollectionTimestamp(node.get("patientGAAtCollectionTimestamp").numberValue().floatValue());
                if (node.has("orderNumber"))
                    report.setOrderNumber(node.get("orderNumber").textValue());
                if (node.has("orderId"))
                    report.setOrderId(node.get("orderId").textValue());
                if (node.has("laboratoryOrderId"))
                    report.setLaboratoryOrderId(node.get("laboratoryOrderId").textValue());
                if (node.has("batchRunId"))
                    report.setBatchRunId(node.get("batchRunId").textValue());
                if (node.has("labId"))
                    report.setLabId(node.get("labId").textValue());
                if (node.has("pdfStorageKeyName"))
                    report.setPdfStorageKeyName(node.get("pdfStorageKeyName").textValue());
                if (node.has("pdfStorageBucketName"))
                    report.setPdfStorageBucketName(node.get("pdfStorageBucketName").textValue());

                if (node.has("noOrder"))
                    report.setNoOrder(node.get("noOrder").booleanValue());
                if (node.has("reportable"))
                    report.setReportable(node.get("reportable").booleanValue());
                if (node.has("approved"))
                    report.setApproved(node.get("approved").booleanValue());
                if (node.has("rejected"))
                    report.setRejected(node.get("rejected").booleanValue());
                if (node.has("signedOut"))
                    report.setSignedOut(node.get("signedOut").booleanValue());
                if (node.has("noOrder"))
                    report.setNoOrder(node.get("noOrder").booleanValue());
                if (node.has("pipelineRunId"))
                    report.setPipelineRunId(node.get("pipelineRunId").textValue());
                if (node.has("sequenceRunId"))
                    report.setSequenceRunId(node.get("sequenceRunId").textValue());
                if (node.has("completedAt")) {
                    String completedAt = node.get("completedAt").textValue();
                    if (completedAt != null) {
                        Date date = sdf.parse(completedAt);
                        Calendar cal = Calendar.getInstance();
                        cal.setTime(date);
                        report.setCompletedAt(cal);
                    }
                }
                if (node.has("reportType"))
                    report.setReportType(ReportType.valueOf(node.get("reportType").textValue()));
                if (node.has("sampleNumber"))
                    report.setSampleNumber(node.get("sampleNumber").textValue());
                if (node.has("deliveredToProvider"))
                    report.setDeliveredToProvider(node.get("deliveredToProvider").booleanValue());
                if (node.has("deliveredToPatient"))
                    report.setDeliveredToPatient(node.get("deliveredToPatient").booleanValue());
                if (node.has("deliveredToPatientAt")) {
                    String completedAt = node.get("deliveredToPatientAt").textValue();
                    if (completedAt != null) {
                        Date date = sdf.parse(completedAt);
                        Calendar cal = Calendar.getInstance();
                        cal.setTime(date);
                        report.setDeliveredToPatientAt(cal);
                    }
                }
                if (node.has("viewedByPatient"))
                    report.setViewedByPatient(node.get("viewedByPatient").booleanValue());
                if (node.has("viewedByPatientAt")) {
                    String completedAt = node.get("viewedByPatientAt").textValue();
                    if (completedAt != null) {
                        Date date = sdf.parse(completedAt);
                        Calendar cal = Calendar.getInstance();
                        cal.setTime(date);
                        report.setViewedByPatientAt(cal);
                    }
                }
                if (node.has("reportable"))
                    report.setReportable(node.get("reportable").booleanValue());
                if (node.has("available"))
                    report.setAvailable(node.get("available").booleanValue());
                if (node.has("resultData")) {
                    JsonNode resultsData = node.get("resultData");

                    Report results = new Report();
                    report.setResultData(results);
                    if (resultsData != null) {
                        if (resultsData.has("reportName"))
                            results.setReportName(resultsData.get("reportName").textValue());
                        if (resultsData.has("qc")) {
                            JsonNode qc = resultsData.get("qc");
                            results.setQc(mapper.readValue(qc.toString(), TestQC.class));
                            if (resultsData.has("data")) {
                                JsonNode d = resultsData.get("data");
                                if (report.getReportConfiguration() != null) {
                                    switch (report.getReportConfiguration()) {
                                        case FST:
                                            results.setRawData(mapper.readValue(d.toString(), FSTRawData.class));
                                            break;
                                        case NIPS_BASIC:
                                            results.setRawData(mapper.readValue(d.toString(), NIPSBasicRawData.class));
                                            break;
                                        case NIPS_PLUS:
                                            results.setRawData(mapper.readValue(d.toString(), NIPSPlusRawData.class));
                                            break;
                                        case NIPS_ADVANCED:
                                            results.setRawData(mapper.readValue(d.toString(), NIPSAdvancedRawData.class));
                                            break;
                                    }
                                }
                            }
                            if (resultsData.has("strings")) {
                                JsonNode d = resultsData.get("strings");
                                if (report.getReportConfiguration() != null) {
                                    switch (report.getReportConfiguration()) {
                                        case NIPS_BASIC:
                                            results.setStrings(mapper.readValue(d.toString(), NIPSReportStrings.class));
                                            break;
                                        case NIPS_PLUS:
                                            results.setStrings(mapper.readValue(d.toString(), NIPSReportStrings.class));
                                            break;
                                        case NIPS_ADVANCED:
                                            results.setStrings(mapper.readValue(d.toString(), NIPSReportStrings.class));
                                            break;
                                    }
                                }
                            }
                        }
                    }
                }
                return report;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        return null;
    }
}

