function AppAutoContext() {
    const ctx =  Packages.cc.appauto.lib.ng.AppAutoContext.INSTANCE;
    this.automatorOf = (name) => ctx.automatorOf(name);
}

exports.AppAutoContext = new AppAutoContext();
exports.Log = Packages.android.util.Log;
exports.dateStr = 2