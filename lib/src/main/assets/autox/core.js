
 AppAutoContext = {
    ctx: Packages.cc.appauto.lib.ng.AppAutoContext.INSTANCE,
    automatorOf: (name) => ctx.automatorOf(name),
}

function sprintf (args) {
    const String = Packages.java.lang.String;
    if (args.length === 1) return args[0];

    if (Array.isArray()) {
        return String.format.apply(null, args);
    }
    return String.format.apply(null, Array.from(args));
}

function Logger(tag) {
    const Log = Packages.android.util.Log;
    const TAG = tag == null ? Packages.cc.appauto.lib.ng.TAG : tag;
    const levels = {
        verbose: "v",
        debug: "d",
        info: "i",
        warn: "w",
        error: 'e'
    };

    Object.keys(levels).forEach(key => {
        this[key]= function() {
            const content = sprintf(arguments);
            const val = levels[key];
            Log[val].apply(null, [TAG, content]);
        };
    });
}

const __CORE_LOADED__ = new Date().toString();