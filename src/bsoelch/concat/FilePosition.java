package bsoelch.concat;

import java.util.Objects;

class FilePosition {
    static boolean ID_MODE=false;

    final String fileId;
    final String path;
    final long line;
    final int posInLine;
    final FilePosition expandedAt;

    FilePosition(String fileId,String path, long line, int posInLine) {
        this.fileId=fileId;
        this.path = path;
        this.line = line;
        this.posInLine = posInLine;
        expandedAt = null;
    }

    FilePosition(FilePosition at, FilePosition expandedAt) {
        this.fileId=at.fileId;
        this.path = at.path;
        this.line = at.line;
        this.posInLine = at.posInLine;
        this.expandedAt = expandedAt;
    }

    @Override
    public String toString() {
        String ret= (ID_MODE?fileId:path) + ":" + line + ":" + posInLine;
        if (expandedAt != null) {
            ret+="\nexpanded at " + expandedAt;
        }
        return ret;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FilePosition that = (FilePosition) o;
        return line == that.line && posInLine == that.posInLine &&
                Objects.equals(path, that.path) && Objects.equals(expandedAt, that.expandedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, line, posInLine, expandedAt);
    }
}
