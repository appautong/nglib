var out = java.lang.System.out;

var Automator = Packages.cc.appauto.lib.ng.AppAutomator;

// srv points to the AccessibilityService instance in your application
var srv = Packages.cc.appauto.ng.client.AccessService.inst;

var auto = new Automator(srv);

auto.newOpeningAppStep("com.tencent.mm");

auto.newStep("step2").action({
    invoke: (step) => {
        out.println("step2 action: " + step);
    }
}).expect({
    invoke: (ht, step) => {
        out.println("in step2 expect: " + ht.print());
        return true;
    }
});

var succ = auto.run().allStepsSucceed;

out.println("run result: " + succ);
out.println(auto.message == null? "null": auto.message);

auto.close();
