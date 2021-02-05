declare function sprintf(format: string, ...args: any[]): string;

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

declare var Logger: LoggerConstructor

