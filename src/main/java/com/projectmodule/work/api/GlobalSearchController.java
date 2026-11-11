package com.projectmodule.work.api;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.work.application.GlobalSearchApplicationService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Search across every project the caller belongs to. */
@RestController
public class GlobalSearchController {

    private final GlobalSearchApplicationService service;
    private final RequestContext requestContext;

    public GlobalSearchController(GlobalSearchApplicationService service, RequestContext requestContext) {
        this.service = service;
        this.requestContext = requestContext;
    }

    @GetMapping("/search")
    public List<GlobalSearchApplicationService.Hit> search(@RequestParam("q") String query) {
        return service.search(requestContext, query);
    }
}
