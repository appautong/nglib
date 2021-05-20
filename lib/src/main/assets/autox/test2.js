(function() {
    const logger = new Logger("autojs");
    logger.info("test2 started...")

    const automator = AppAutoContext.automatorOf("autojs_test2");
    automator.stepOfOpeningApp("com.tencent.mm");

    const s = automator.stepOf("search given contact");
    s.setupActionNode("search_button", (tree) => {
        const selector = sprintf("%s>%s", ClassName.ViewGroup, ClassName.RelativeLayout);
        return tree.classHierarchySelector(selector).contentDescription("搜索");
    });
    s.action((step) => {
        const search = step.getActionNodeInfo("search_button");
        logger.info("search is: %s\n", search);
        AppAutoContext.click(search)
    }).expect((tree, step) => {
        logger.info("in expect handler: ")
        return true;
    });

    const succ = automator.run().allStepsSucceed;
    logger.info("test2 end, succ: %s, message: %s\n", succ, automator.message);

    automator.close();
    AppAutoContext.dumpTopActiveApp();

    return "test2.js runs successfully";
})();