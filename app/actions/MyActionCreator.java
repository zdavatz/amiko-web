package actions;

import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Result;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletionStage;

import java.lang.reflect.Method;

public class MyActionCreator implements play.http.ActionCreator {
    @Override
    public Action createAction(Http.Request request, Method actionMethod) {
        return new Action.Simple() {
            @Override
            public CompletionStage<Result> call(Http.Context ctx) {
                Path path = Paths.get(ctx.request().path());
                if (path.toString().contains("lang")) {
                    String lang = path.getName(1).toString();
                    // we detect language only by URL path, cookies does not used
                    ctx.changeLang(lang);
                }
                return delegate.call(ctx);
            }
        };
    }
}
