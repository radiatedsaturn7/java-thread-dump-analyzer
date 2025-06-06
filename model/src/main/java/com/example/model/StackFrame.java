package com.example.model;

public class StackFrame {
    private final String className;
    private final String methodName;
    private final String fileName;
    private final int lineNumber;

    public StackFrame(String className, String methodName, String fileName, int lineNumber) {
        this.className = className;
        this.methodName = methodName;
        this.fileName = fileName;
        this.lineNumber = lineNumber;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getFileName() {
        return fileName;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        StackFrame other = (StackFrame) obj;
        if (lineNumber != other.lineNumber) return false;
        if (className == null ? other.className != null : !className.equals(other.className)) return false;
        if (methodName == null ? other.methodName != null : !methodName.equals(other.methodName)) return false;
        if (fileName == null ? other.fileName != null : !fileName.equals(other.fileName)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int result = className != null ? className.hashCode() : 0;
        result = 31 * result + (methodName != null ? methodName.hashCode() : 0);
        result = 31 * result + (fileName != null ? fileName.hashCode() : 0);
        result = 31 * result + lineNumber;
        return result;
    }

    @Override
    public String toString() {
        return className + "." + methodName + "(" + fileName + ":" + lineNumber + ")";
    }
}
