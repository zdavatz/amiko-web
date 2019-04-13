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
            public CompletionStage<Result> call(Http.Context ctx) {
                Http.Request request = ctx.request();
                String host = request.host();
                if (host.startsWith("amiko")) {
                    ctx.changeLang("de");
                } else if (host.startsWith("comed")) {
                    ctx.changeLang("fr");
                }
                return delegate.call(ctx);
            }
        };
    }
}
