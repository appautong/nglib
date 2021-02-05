/// <reference path="./core.d.ts" />

// AutomationStep constructor
function AutomationStep(name, javaAutomator) {
    const javaStep = javaAutomator.stepOf(name);

    const javaReadonlyProperties = ["expectedSuccess", "message"];
    javaReadonlyProperties.forEach(prop => {
        Object.defineProperty(this, prop, {
            get: () => javaStep[prop]
        });
    });

    const javaMethods = ["retry", "postActionDelay"]
    javaMethods.forEach(method => {
        this[method] = function() {
            return javaStep[method].apply(javaStep, arguments);
        }
    });

    const funcParameterMethods = ["action", "expect"];
    funcParameterMethods.forEach(method => {
        this[method] = function(func) {
            return javaStep[method]({invoke: func});
        }
    });

    return this;
}

// Automator constructor
function Automator(name) {
    const ctx = Packages.cc.appauto.lib.ng.AppAutoContext.INSTANCE;
    const javaAutomator = ctx.automatorOf(name);

    // add a new step with given name
    this.stepOf = function(name) {
        const step = new AutomationStep(name, javaAutomator);
        return step;
    };

    const javaReadonlyProperties = ["allStepsSucceed", "message"];
    javaReadonlyProperties.forEach(prop => {
        Object.defineProperty(this, prop, {
            get: () => javaAutomator[prop]
        });
    });

    const javaMethods = ["stepOfOpeningApp", "run", "close"]
    javaMethods.forEach(method => {
        this[method] = function() {
            return javaAutomator[method].apply(javaAutomator, arguments);
        }
    });

    return this;
}

// singleton AppAutoContext instance
var AppAutoContext = (function() {
    const javaAppAutoContext = Packages.cc.appauto.lib.ng.AppAutoContext.INSTANCE;
    const javaAppAutomatorClass = Packages.cc.appauto.lib.ng.AppAutomator;

    const instance = {
        automatorOf: (name) => new Automator(name),
    };

    const javaReadonlyProperties = ["accessibilityConnected", "listenerConnected"];
    javaReadonlyProperties.forEach(prop => {
        Object.defineProperty(instance, prop, {
            get: () => javaAppAutoContext[prop]
        });
    });

    Object.defineProperty(instance, "automatorDefaultRetryCount", {
        get: () => javaAppAutomatorClass["defaultRetryCount"],
        set: v => javaAppAutomatorClass["defaultRetryCount"] = v
    });
    Object.defineProperty(instance, "automatorDefaultPostActionDelay", {
        get: () => javaAppAutomatorClass["defaultPostActDelay"],
        set: v => javaAppAutomatorClass["defaultPostActDelay"] = v
    });

    return instance;
})();


function sprintf(args) {
    const String = Packages.java.lang.String;
    // args is a arguments object passed from caller
    if (typeof(args) === 'object' && args.hasOwnProperty("callee") && args.hasOwnProperty("length")) {
        return String.format.apply(null, Array.from(args));
    }

    if (Array.isArray(args)) {
        return String.format.apply(null, args);
    }

    return String.format.apply(null, Array.from(arguments));
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
            // todo: deal with maximum logcat entry size
            Log[val].apply(null, [TAG, content]);
        };
    });
}

function javaCollectionToArray(javaCollection) {
    const ret = [];
    javaCollection.forEach(elem => {
        ret.push(elem);
    });
    return ret
}

var ClassName = Packages.cc.appauto.lib.ng.ClassName;

const __CORE_LOADED__ = new Date().toString();