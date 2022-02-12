package bsoelch.concat;

import java.util.Objects;

class FilePosition {
    final String path;
    final long line;
    final int posInLine;
    final FilePosition expandedAt;

    FilePosition(String path, long line, int posInLine) {
        this.path = path;
        this.line = line;
        this.posInLine = posInLine;
        expandedAt = null;
    }

    FilePosition(FilePosition at, FilePosition expandedAt) {
        this.path = at.path;
        this.line = at.line;
        this.posInLine = at.posInLine;
        this.expandedAt = expandedAt;
    }

    @Override
    public String toString() {
        if (expandedAt != null) {
            return path + ":" + line + ":" + posInLine +
                    "\nexpanded at " + expandedAt;
        } else {
            return path + ":" + line + ":" + posInLine;
        }
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
