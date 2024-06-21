package controllers;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.JsonNode;
import models.ViewContext;
import play.i18n.Lang;
import play.i18n.Messages;
import play.libs.ws.WSBodyReadables;
import javax.inject.Inject;
import play.libs.ws.*;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;

import views.html.index;
import views.html.oauthcallback;

public class OAuthController extends Controller {
    static final String HIN_CLIENT_ID = "ch.ywesee";
    static final String HIN_CLIENT_SECRET = "";

    private final WSClient ws;

    @Inject
    public OAuthController(WSClient ws) {
        this.ws = ws;
    }

    public String authUrlWithApplicationName(String applicationName) {
        return "https://apps.hin.ch/REST/v1/OAuth/GetAuthCode/" + applicationName + "?response_type=code&client_id=" + HIN_CLIENT_ID + "&redirect_uri=" + this.redirectUri() + "&state=" + applicationName;
    }

    public String redirectUri() {
        return "amiko://oauth";
    }

    public Result sdsAuth(Http.Request request) {
        return redirect(authUrlWithApplicationName("hin_sds"));
        // ADSwiss_CI-Test
        // ADSwiss_CI
    }
    public Result adswissAuth(Http.Request request) {
        // ADSwiss_CI-Test
        // ADSwiss_CI
        return redirect(authUrlWithApplicationName("ADSwiss_CI-Test"));
    }

    public CompletionStage<Result> oauthCallback(Http.Request request, String code, String state) {
        String subdomainLang = request.transientLang().orElse(Lang.forCode("de")).language();
        String redirectDest  = subdomainLang == "de" ? "/rezept" : "/prescription";
        return fetchAccessTokenFromOAuthCode(code)
        .thenApply((String accessTokenRes) -> {
            return ok(oauthcallback.render(redirectDest, accessTokenRes));
        });
    }

    public Result adswissCallback(Http.Request request, String authCode) {
        // TODO
        ViewContext ctx = new ViewContext();
        return ok(index.render("", "", "", "", "", ctx, null));
    }

    public CompletionStage<String> fetchAccessTokenFromOAuthCode(String code) {
        CompletionStage<WSResponse> request = ws.url("https://oauth2.hin.ch/REST/v1/OAuth/GetAccessToken")
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .post(
                "grant_type=authorization_code"
                + "&redirect_uri=" + URLEncoder.encode(this.redirectUri(), StandardCharsets.UTF_8)
                + "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                + "&client_id=" + HIN_CLIENT_ID
                + "&client_secret=" + HIN_CLIENT_SECRET
            );
        return request.thenApply(
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
                    + "&redirect_uri=" + URLEncoder.encode(this.redirectUri(), StandardCharsets.UTF_8)
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

}

