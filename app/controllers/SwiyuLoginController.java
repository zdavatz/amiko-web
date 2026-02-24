/*
Copyright (c) 2026 ywesee GmbH

swiyu Wallet Login integration for AmiKoWeb.
Implements OID4VP (OpenID for Verifiable Presentations) login flow.
*/

package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.libs.Json;
import play.libs.ws.WSClient;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.i18n.MessagesApi;
import play.i18n.Messages;
import views.html.swiyu_login;

import com.typesafe.config.Config;
import javax.inject.Inject;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class SwiyuLoginController extends Controller {

    // swiyu Verifier Management URL (via Apache proxy, IP-whitelist: 65.109.136.203)
    private static final String VERIFIER_MANAGEMENT_URL =
        "https://swiyu.ywesee.com/verifier-mgmt/api";

    private static final String DOCTOR_CREDENTIAL_VCT = "doctor-credential-sdjwt";

    private final String acceptedIssuerDid;

    // Session keys
    public static final String SESSION_AUTH      = "swiyu_auth";
    public static final String SESSION_GLN       = "swiyu_gln";
    public static final String SESSION_FIRSTNAME = "swiyu_firstName";
    public static final String SESSION_LASTNAME  = "swiyu_lastName";

    @Inject WSClient ws;
    @Inject MessagesApi messagesApi;

    @Inject
    public SwiyuLoginController(Config config) {
        this.acceptedIssuerDid = config.getString("swiyu.accepted_issuer_did");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /swiyu  →  Login page with QR widget
    // ─────────────────────────────────────────────────────────────────────────

    public Result loginPage(Http.Request request) {
        if (isAuthenticated(request)) {
            return redirect(controllers.routes.MainController.index(""));
        }
        Messages messages = messagesApi.preferred(request);
        return ok(swiyu_login.render(messages));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /swiyu/login  →  {verification_id, deeplink}
    // ─────────────────────────────────────────────────────────────────────────

    public CompletionStage<Result> initiateLogin(Http.Request request) {
        ObjectNode body = Json.newObject();
        body.putArray("accepted_issuer_dids").add(acceptedIssuerDid);
        body.put("response_mode", "direct_post");

        ObjectNode pd = body.putObject("presentation_definition");
        pd.put("id", UUID.randomUUID().toString());

        ObjectNode desc = pd.putArray("input_descriptors").addObject();
        desc.put("id", UUID.randomUUID().toString());

        ObjectNode sdJwt = desc.putObject("format").putObject("vc+sd-jwt");
        sdJwt.putArray("sd-jwt_alg_values").add("ES256");
        sdJwt.putArray("kb-jwt_alg_values").add("ES256");

        ObjectNode constraints = desc.putObject("constraints");
        addField(constraints, "$.vct",       DOCTOR_CREDENTIAL_VCT, true);
        addField(constraints, "$.firstName", null, false);
        addField(constraints, "$.lastName",  null, false);
        addField(constraints, "$.gln",       null, false);

        return ws.url(VERIFIER_MANAGEMENT_URL + "/verifications")
            .setContentType("application/json")
            .addHeader("SWIYU-API-Version", "1")
            .post(body)
            .thenApply(response -> {
                if (response.getStatus() != 201 && response.getStatus() != 200) {
                    return internalServerError(Json.newObject()
                        .put("error", "Verifier nicht erreichbar: " + response.getStatus()));
                }
                JsonNode json = response.asJson();
                ObjectNode result = Json.newObject();
                result.put("verification_id", json.get("id").asText());
                result.put("deeplink",        json.get("verification_deeplink").asText());
                return ok(result);
            });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /swiyu/status/:id  →  {state, claims?, authenticated?}
    // ─────────────────────────────────────────────────────────────────────────

    public CompletionStage<Result> checkStatus(Http.Request request, String verificationId) {
        return ws.url(VERIFIER_MANAGEMENT_URL + "/verifications/" + verificationId)
            .get()
            .thenApply(response -> {
                if (response.getStatus() != 200) {
                    return notFound(Json.newObject().put("error", "Nicht gefunden"));
                }
                JsonNode json  = response.asJson();
                String   state = json.path("state").asText("PENDING");

                ObjectNode result = Json.newObject();
                result.put("state", state);

                if ("SUCCESS".equals(state)) {
                    JsonNode claims = extractClaims(json);
                    if (claims != null && !claims.path("gln").asText("").isEmpty()) {
                        result.set("claims", claims);
                        result.put("authenticated", true);
                    } else {
                        result.put("authenticated", false);
                        result.put("error", "Claims konnten nicht extrahiert werden");
                    }
                }
                return ok(result);
            });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /swiyu/session  →  Create Play session from verified claims
    // nocsrf in routes (called from JS, not a form)
    // ─────────────────────────────────────────────────────────────────────────

    public CompletionStage<Result> createSession(Http.Request request) {
        JsonNode body = request.body().asJson();
        if (body == null) {
            return CompletableFuture.completedFuture(
                badRequest(Json.newObject().put("error", "Kein JSON-Body")));
        }

        String verificationId = body.path("verification_id").asText("").trim();
        if (verificationId.isEmpty()) {
            return CompletableFuture.completedFuture(
                badRequest(Json.newObject().put("error", "verification_id fehlt")));
        }

        // Re-fetch verification result from the verifier API (server-side)
        return ws.url(VERIFIER_MANAGEMENT_URL + "/verifications/" + verificationId)
            .get()
            .thenApply(response -> {
                if (response.getStatus() != 200) {
                    return badRequest(Json.newObject()
                        .put("error", "Verifikation nicht gefunden"));
                }

                JsonNode json  = response.asJson();
                String   state = json.path("state").asText("");
                if (!"SUCCESS".equals(state)) {
                    return forbidden(Json.newObject()
                        .put("error", "Verifikation nicht erfolgreich: " + state));
                }

                JsonNode claims = extractClaims(json);
                if (claims == null || claims.path("gln").asText("").isEmpty()) {
                    return forbidden(Json.newObject()
                        .put("error", "Claims konnten nicht extrahiert werden"));
                }

                String firstName = claims.path("firstName").asText("").trim();
                String lastName  = claims.path("lastName").asText("").trim();
                String gln       = claims.path("gln").asText("").trim();

                if (!gln.matches("^760\\d{10}$")) {
                    return forbidden(Json.newObject()
                        .put("error", "Ungültiges GLN-Format"));
                }
                if (firstName.isEmpty() || lastName.isEmpty()) {
                    return badRequest(Json.newObject()
                        .put("error", "Name fehlt in den Claims"));
                }

                java.util.Map<String,String> session = new java.util.HashMap<>();
                session.put(SESSION_AUTH,      "true");
                session.put(SESSION_GLN,       gln);
                session.put(SESSION_FIRSTNAME, firstName);
                session.put(SESSION_LASTNAME,  lastName);
                return ok(Json.newObject()
                        .put("status", "ok")
                        .put("name",   firstName + " " + lastName)
                        .put("gln",    gln))
                    .withSession(session);
            });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /swiyu/logout
    // ─────────────────────────────────────────────────────────────────────────

    public Result logout(Http.Request request) {
        return redirect(controllers.routes.MainController.index(""))
            .withNewSession();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Static helpers for other controllers
    //
    //   if (!SwiyuLoginController.isAuthenticated(request)) {
    //       return redirect(controllers.routes.SwiyuLoginController.loginPage());
    //   }
    //   String gln = SwiyuLoginController.getGln(request);
    // ─────────────────────────────────────────────────────────────────────────

    public static boolean isAuthenticated(Http.Request request) {
        return request.session().get(SESSION_AUTH)
            .map("true"::equals)
            .orElse(false);
    }

    public static String getGln(Http.Request request) {
        return request.session().get(SESSION_GLN).orElse("");
    }

    public static String getFullName(Http.Request request) {
        String first = request.session().get(SESSION_FIRSTNAME).orElse("");
        String last  = request.session().get(SESSION_LASTNAME).orElse("");
        return (first + " " + last).trim();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private JsonNode extractClaims(JsonNode verificationResult) {
        try {
            // swiyu verifier returns claims at wallet_response.credential_subject_data
            JsonNode claims = verificationResult.path("wallet_response")
                                                .path("credential_subject_data");
            if (claims.isMissingNode() || claims.isNull())
                return null;

            ObjectNode result = Json.newObject();
            result.put("firstName", claims.path("firstName").asText(""));
            result.put("lastName",  claims.path("lastName").asText(""));
            result.put("gln",       claims.path("gln").asText(""));
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    private void addField(ObjectNode constraints, String path,
                          String filterValue, boolean isFilter) {
        ObjectNode field = constraints.withArray("fields").addObject();
        field.putArray("path").add(path);
        if (isFilter && filterValue != null) {
            field.putObject("filter")
                .put("type",  "string")
                .put("const", filterValue);
        }
    }
}
