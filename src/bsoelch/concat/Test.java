package bsoelch.concat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

public class Test {

    public static void main(String[] args) throws IOException {
        File tests=new File("./tests");
        File[] files=tests.listFiles();
        PrintStream out;
        PrintStream err;
        Interpreter.IOContext context;
        if(files!=null){
            for(File file:files){
                String path=file.getAbsolutePath();
                if(path.endsWith(Interpreter.DEFAULT_FILE_EXTENSION)){
                    String reducedPath = path.substring(0, path.length() - Interpreter.DEFAULT_FILE_EXTENSION.length());
                    out=new PrintStream(new FileOutputStream(reducedPath +".out.txt"));
                    err=new PrintStream(new FileOutputStream(reducedPath +".err.txt"));
                    context=new Interpreter.IOContext(out,err);
                    Interpreter.compileAndRun(path,context);
                }
            }
        }else{
            throw new IOException("unable to list files");
        }
    }
}
