/*
var ctx = Packages.cc.appauto.lib.ng.AppAutoContext.INSTANCE;
var auto = ctx.automatorOf("test_automator")

auto.stepOfOpeningApp("com.tencent.mm");

auto.stepOf("step2").action({
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
*/

var appauto = require('appauto');
appauto.Log.i("test", "appauto module is loaded");
appauto.Log.i("test", appauto.dateStr);

var automator = appauto.AppAutoContext.automatorOf("test automator");
appauto.Log.i("test", "automator is: " + automator.name);