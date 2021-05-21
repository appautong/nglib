
(function() {
    const logger = new Logger("autojs");
    logger.info("test3 started...")
    Packages.cc.appauto.lib.ng.wechat.Wechat.openMePage();

    logger.info("test3 end")


    return "test3.js runs successfully";
})();
