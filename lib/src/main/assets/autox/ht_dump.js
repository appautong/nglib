// run codes in a anonymous function scope
(function(){
    const logger = new Logger("ht_dump");
    logger.info("core loaded at: %s\n", __CORE_LOADED__);

    logger.info("hierarchy string:\n%s\n", AppAutoContext.topAppHierarchyString);
    logger.info("dump with indent:\n");
    AppAutoContext.dumpTopActiveApp();
})();