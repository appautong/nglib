interface Logger {
    verbose(format: string, ...args: any[]): void;
    debug(format: string, ...args: any[]): void;
    info(format: string, ...args: any[]): void;
    warn(format: string, ...args: any[]): void;
    error(format: string, ...args: any[]): void;
}

interface LoggerConstructor {
    new(tag?: string): Logger;
}

interface javaIterable<T> {
    forEach(consumer: (elem: T) => void);
}

interface JavaCollection<T> extends javaIterable<T> {
    size(): number;
    isEmpty(): boolean;
    get(index: number): T;
}

interface ClassName {
    Button: string;
    EditText: string;
    FrameLayout: string;
    ImageView: string;
    ImageButton: string;
    Linearlayout: string;
    ListView: string;
    RecyclerView: string;
    RelativeLayout: string;
    TextView: string;
    View: string;
    ViewGroup: string;
    ViewPager: string;
    WebView: string;
}



interface SelectionResult {
    nodes: JavaCollection<HierarchyNode>;

    // further selection against class name
    className(value: string): SelectionResult;

    // further selection against control text
    text(value: string, exactlyMatch?: boolean): SelectionResult;

    // further selection against control content description
    contentDescription(value: string, exactlyMatch?: boolean): SelectionResult;

    // further selection against control visibility
    isVisibleToUser(value: string): SelectionResult;

    // custom selector against given predicate
    selector(predicate: (hn: HierarchyNode) => boolean) : SelectionResult;
}

interface Rect {
    left: number;
    right: number;
    top: number;
    bottom: number;
    width(): number;
    height(): number;
    centerX(): number;
    centerY(): number;
    toShortString(): string;
    toString(): string;
}

interface AccessibilityNodeInfo {

}

interface JSONObject {
    toJSONString(): string;
    getString(key: string): string;
}

interface HierarchyNode {
    // depth level in the hierarchy tree, 0 for node without parent
    depth: number;

    parent: HierarchyNode|null;

    children: JavaCollection<HierarchyNode>;

    // siblings equals to parent.children
    siblings: JavaCollection<HierarchyNode>;

    // this node's index in the sibling list; negative for node without parent
    siblingIndex: number;

    windowId: number;
    viewId: string|null;
    className: string;
    text: string|null;
    contentDescription: string|null;
    bound: Rect|null;
    isVisibleToUser: boolean;
    isClickable: boolean;
    isScrollable: boolean;
    isEditable: boolean;

    hierarchyString: string;
    string: string;
    stringIndent: string;
    hierarchyId: string;

    ancestor(n:number): HierarchyNode|null;

    // walk down the hierarchy to leaf node and call predicate f on each element (include the current node)
    filter(f: (hn: HierarchyNode) => boolean): JavaCollection<HierarchyNode>;

    // walk up the hierarchy to root(top) node and call predicate f on each element (include the current node)
    filterAncestor(f: (hn: HierarchyNode) => boolean): JavaCollection<HierarchyNode>;

    // find at most n ancestor with the predicate f; find all if n <= 0
    findAncestor(f: (hn: HierarchyNode) => boolean, n: number): JavaCollection<HierarchyNode>;

    /** return sibling of current node
     * for example, if parameter n is:
     * * 0: return current
     * * 1: next sibling
     * * -1: previous sibling
     */
    sibling(n: number): HierarchyNode|null;
    toString(): string;
    walk(f: (hn: HierarchyNode) => void): void;

    // walk up the hierarchy to root(top) node and call f on each element (include the current node)
    walkAncestor(f: (hn: HierarchyNode) => void): void;
}

interface HierarchyTree {
    windowId: number;
    packageName: string;
    hierarchyString: string;

    print(): void;

    // recycle the hierarchy tree object to release resources.
    // no further operation shall be done after recycle
    recycle(): void;

    /**get selection result against given class selector string on the hierarchy tree.
     * the class selector string could composite of following class relationship:
     * * descendant: A B <p>B is a descendant of A, such as child, child of child, etc.</p>
     * * child: A > B <p>B is directly child of A</p>
     *
     * e.g.: A > B C D > E F > G
     */
    classHierarchySelector(selector: string): SelectionResult;
}

interface AutomationStep {
    // whether expectation of step return success
    expectedSuccess: boolean;

    // descriptive message of the run result
    message: string;

   // set the step action to be execuated
    action(act: (step: AutomationStep) => void): AutomationStep;

    // set the expectation to valid the successful result of action
    expect(predicate: (tree: HierarchyTree, step: AutomationStep) => boolean): AutomationStep;

    // set the retry count; the action will be retired n-times if expect predicate not matched
    retry(n: number): AutomationStep;

    // set the delay after executed the action, default 2000ms
    postActionDelay(ms: number): AutomationStep;

    // set the delay before checking all action target, default 0ms
    preActionDelay(ms: number): AutomationStep;

    // setupActionNode add a constraint on given action node
    // all action nodes shall be ready before the action executed
    setupActionNode(name: string, filter:  (tree: HierarchyTree) => SelectionResult);
    setupOptionalActionNode(name: string, filter:  (tree: HierarchyTree) => SelectionResult);

    getActionNodeInfo(name: string): any;
    actionTargetIsFound(name: string): boolean;
}

interface Automator {
    // append a new step with given name
    stepOf(name: string): AutomationStep;

    // append a new step with opening given app
    stepOfOpeningApp(packageName: string): AutomationStep;

    // run all the steps of given automator
    run(): Automator;

    // close the automator
    close();

    // whether all steps of the automator are executed successfully
    allStepsSucceed: Boolean;

    // descriptive message of the run result
    message: string;

    failedHierarchyString: string;
}


interface AppAutoContextConstructor {
    // create a new automator instance with given name
    automatorOf(name): Automator;

    // dump top active app hierarchy
    dumpTopActiveApp();

    // whether accessibility service is connected now
    accessibilityServiceConnected: boolean;

    // whehter notification service is connected now
    notificationListenerConnected: boolean;

    // default 3; this value will take effect for all automation step created thereafter
    automatorDefaultRetryCount: number;

    // default 1000, unit: ms; this value will take effect for all automation step created thereafter
    automatorDefaultPostActionDelay: number;

    topAppHierarchyString: string;

    click(node: AccessibilityNodeInfo, useCoordinate?: boolean);
    scrollUpDown(isUp: boolean, screenPercent: number, duration: number);
    setContent(node: AccessibilityNodeInfo, content: string): JSONObject;
}

declare function sprintf(format: string, ...args: any[]): string;
declare function javaCollectionToArray<T>(javaCollection: JavaCollection<T>): T[];

declare var AppAutoContext: AppAutoContextConstructor;
declare var ClassName: ClassName;
declare var Logger: LoggerConstructor