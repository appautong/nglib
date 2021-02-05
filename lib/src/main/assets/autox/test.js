// run codes in a anonymous function scope
(function(){
    const logger = new Logger("testjs");
    logger.info("core loaded at: %s\n", __CORE_LOADED__);

    logger.info("accessibilityConnected: %s\n", AppAutoContext.accessibilityConnected);
    logger.info("listenerConnected: %s\n", AppAutoContext.listenerConnected);

    logger.info("default post action delay %s\n", AppAutoContext.automatorDefaultPostActionDelay);
    logger.info("default retry count: %s\n", AppAutoContext.automatorDefaultRetryCount);

    const automator = AppAutoContext.automatorOf("testjs");
    automator.stepOfOpeningApp("com.tencent.mm");
    automator.stepOf("dummy")
        .action(() => logger.info("dummy action executed"))
        .expect((tree) => {
            logger.info("dummy expectation return true directly\n");
            const selector = sprintf("%s>%s", ClassName.RelativeLayout, ClassName.TextView);
            logger.info("selector is: %s\n", selector);
            const res = tree.classNameSelector(selector).selector(hn => {
                return hn.siblingIndex == 0
            });

            // use the java collectoin directly
            logger.info("res.nodes length: %s\n", res.nodes.size());
            res.nodes.forEach(elem => {
               logger.info("forEach java collection: %s\n", elem.string);
            });

            // convert to javascript array
            const nodes = javaCollectionToArray(res.nodes);
            logger.info("nodes.length: %s\n", nodes.length);
            logger.info("nodes are:\n%s\n", nodes.map(elem => elem.string).join("\n"));
            return true;
        });

    var succ = automator.run().allStepsSucceed;
    logger.info("run result: %s\n", succ);
    logger.info("automator message: %s\n", automator.message);
    automator.close();

    return "teset.js run successfully"
})();