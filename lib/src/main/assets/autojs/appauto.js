const ctx =  Packages.cc.appauto.lib.ng.AppAutoContext.INSTANCE;

var AppAutoContext = {
    automatorOf: function(name) { return ctx.automatorOf(name); }
}

function Logger(tag) {
    const Log = Packages.android.util.Log;
    const TAG = tag;
    const String = Packages.java.lang.String;

    const sprintf = (args) => {
        var content;
        if (args.length > 1)  {
            content = String.format.apply(null, args);
        } else {
            content = args[0];
        }
        return content;
    };
    this.verbose = function() {
        const content = sprintf(arguments);
        Log.v.apply(null, [TAG, content]);
    }
    this.debug = function() {
        const content = sprintf(arguments);
        Log.d.apply(null, [TAG, content]);
    }
    this.info = function() {
        const content = sprintf(arguments);
        Log.i.apply(null, [TAG, content]);
    }
    this.warn = function() {
        const content = sprintf(arguments);
        Log.w.apply(null, [TAG, content]);
    }
    this.error = function() {
        const content = sprintf(arguments);
        Log.e.apply(null, [TAG, content]);
    }
}