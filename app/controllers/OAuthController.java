package controllers;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.JsonNode;
import com.typesafe.config.Config;
import models.ViewContext;
import play.i18n.Lang;
import play.i18n.Messages;
import play.libs.Files;
import play.libs.ws.WSBodyReadables;
import javax.inject.Inject;
import play.libs.ws.*;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;

import views.html.helper.CSRF;
import views.html.index;
import views.html.oauthcallback;
import views.html.adswisscallback;

public class OAuthController extends Controller {
    private static final String HIN_CLIENT_ID = "YOUR_HIN_CLIENT_ID";
    private static final String HIN_CLIENT_SECRET = "YOUR_HIN_CLIENT_SECRET";
    private static final String CERTIFACTION_SERVER = "YOUR_CERTIFACTION_SERVER";
    private static final String CERTIFACTION_TEST_SERVER = "YOUR_CERTIFACTION_TEST_SERVER";

    private final WSClient ws;
    @Inject private Config configuration;

    @Inject
    public OAuthController(WSClient ws) {
        this.ws = ws;
    }

    public String authUrlWithApplicationName(Http.Request request, String applicationName) {
        return "https://apps.hin.ch/REST/v1/OAuth/GetAuthCode/" + applicationName + "?response_type=code&client_id=" + HIN_CLIENT_ID + "&redirect_uri=" + this.redirectUri(request) + "&state=" + applicationName;
    }

    public String redirectUri(Http.Request request) {
//        return "http://localhost:23822/callback";
         String host = request.host();
         return "https://" + host + "/oauth/callback";
    }

    public String adswissRedirectUri(Http.Request request) {
        String host = request.host();
        return "https://" + host + "/oauth/adswiss_callback";
    }

    public Result sdsAuth(Http.Request request) {
        return redirect(authUrlWithApplicationName(request, "hin_sds"));
    }

    public String adswissAppName() {
        if (configuration.getBoolean("feature.adswiss_test")) {
            return "ADSwiss_CI-Test";
        } else {
            return "ADSwiss_CI";
        }
    }

    public Result adswissAuth(Http.Request request) {
        String appName = adswissAppName();
        return redirect(authUrlWithApplicationName(request, appName));
    }

    public String hinDomainForADSwiss() {
        if (configuration.getBoolean("feature.adswiss_test")) {
            return "oauth2.ci-prep.adswiss.hin.ch";
        } else {
            return "oauth2.ci.adswiss.hin.ch";
        }
    }

    public CompletionStage<Result> oauthCallback(Http.Request request, String code, String state) {
        String subdomainLang = request.transientLang().orElse(Lang.forCode("de")).language();
        String redirectDest  = subdomainLang == "de" ? "/rezept" : "/prescription";
        return fetchAccessTokenFromOAuthCode(request, code)
        .thenApply((String accessTokenRes) -> {
            return ok(oauthcallback.render(redirectDest, accessTokenRes));
        });
    }

    public Result adswissCallback(Http.Request request, String authCode) {
        play.filters.csrf.CSRF.Token token = CSRF.getToken(request.asScala());
        String subdomainLang = request.transientLang().orElse(Lang.forCode("de")).language();
        String redirectDest  = subdomainLang == "de" ? "/rezept" : "/prescription";
        return ok(adswisscallback.render(redirectDest, adswissAppName(), token.value()));
    }

    public CompletionStage<String> fetchAccessTokenFromOAuthCode(Http.Request request, String code) {
        return ws.url("https://oauth2.hin.ch/REST/v1/OAuth/GetAccessToken")
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .post(
                "grant_type=authorization_code"
                + "&redirect_uri=" + URLEncoder.encode(this.redirectUri(request), StandardCharsets.UTF_8)
                + "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                + "&client_id=" + HIN_CLIENT_ID
                + "&client_secret=" + HIN_CLIENT_SECRET
            ).thenApply(
          (WSResponse r) -> {
              return r.getBody();
          });
    }

    public CompletionStage<Result> renewOAuthToken(Http.Request request) {
        String[] tokenParams = request.body().asFormUrlEncoded().get("refresh_token");
        if (tokenParams.length == 0) {
            return CompletableFuture.completedFuture(badRequest("Need refresh_token"));
        }
        String refreshToken = tokenParams[0];
        String postBody = "grant_type=refresh_token"
                    + "&redirect_uri=" + URLEncoder.encode(this.redirectUri(request), StandardCharsets.UTF_8)
                    + "&refresh_token=" + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8)
                    + "&client_id=" + HIN_CLIENT_ID
                    + "&client_secret=" + HIN_CLIENT_SECRET;
        System.out.println("postBody: " + postBody);
        return ws.url("https://oauth2.hin.ch/REST/v1/OAuth/GetAccessToken")
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .post(
                    postBody
                )
                .thenApply((WSResponse ws)-> ok(ws.getBody(WSBodyReadables.instance.json())));
    }

    public CompletionStage<Result> fetchAdswissAuthHandle(Http.Request request) {
        String accessToken = request.body().asFormUrlEncoded().get("access_token")[0];
        String authCode = request.body().asFormUrlEncoded().get("auth_code")[0];
        String url = "https://" + hinDomainForADSwiss() + "/authService/EPDAuth/auth_handle";
        return ws.url(url)
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + accessToken)
                .post(
                        "{\"authCode\":\"" + authCode + "\"}"
                )
                .thenApply((WSResponse ws)-> {
                    System.out.println("the body " + ws.getBody().toString());
                    return ok(ws.getBody(WSBodyReadables.instance.json()));
                });
    }

    public CompletionStage<Result> fetchSDSSelfProfile(Http.Request request, String accessToken) {
        return ws.url("https://oauth2.sds.hin.ch/api/public/v1/self/")
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer " + accessToken)
                .get()
                .thenApply((WSResponse res)-> ok(res.getBody(WSBodyReadables.instance.json())));
    }

    public CompletionStage<Result> fetchADSwissSAML(Http.Request request, String accessToken) {
        String url = "https://" + hinDomainForADSwiss() + "/authService/EPDAuth?targetUrl=" + adswissRedirectUri(request) + "&style=redirect";
        return ws.url(url)
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer " + accessToken)
                .post("")
                .thenApply((WSResponse res)-> ok(res.getBody(WSBodyReadables.instance.json())));
    }

    public CompletionStage<Result> makeEPrescriptionQR(Http.Request request, String authHandle) {
        String certifactionServer = configuration.getBoolean("feature.adswiss_test") ? CERTIFACTION_TEST_SERVER : CERTIFACTION_SERVER;
        String url = certifactionServer + "/ePrescription/create?output-format=qrcode";
        return ws.url(url)
                .addHeader("Content-Type", "text/plain")
                .addHeader("Authorization", "Bearer " + authHandle)
                .post(request.body().asText())
                .thenApply((WSResponse res)->
                    ok(res.getBody(WSBodyReadables.instance.bytes()))
                );
    }
}
