package actions;

import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Result;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletionStage;
import play.i18n.Lang;

import java.lang.reflect.Method;

public class MyActionCreator implements play.http.ActionCreator {
    @Override
    public Action createAction(Http.Request request, Method actionMethod) {
        return new Action.Simple() {
            @Override
            public CompletionStage<Result> call(Http.Request req) {
                String host = req.host();
                if (host.startsWith("amiko")) {
                    req = req.withTransientLang(Lang.forCode("de"));
                } else if (host.startsWith("comed")) {
                    req = req.withTransientLang(Lang.forCode("fr"));
                }
                return delegate.call(req);
            }
        };
    }
}
