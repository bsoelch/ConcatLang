package bsoelch.concat;

import java.io.*;

public class Test {

    public static void main(String[] args) throws IOException {
        String testPath=System.getProperty("user.dir")+"/tests/";
        File lib=new File(Interpreter.libPath);
        File[] files=lib.listFiles();

        PrintStream out;
        PrintStream err;
        Interpreter.IOContext context;
        BufferedWriter includeAll = new BufferedWriter(new FileWriter(testPath+"autoGen.includeAll.concat"));
        includeAll.write("test/includeAll :");
        includeAll.newLine();
        out=new PrintStream(new FileOutputStream(testPath+"/autoGen.libFiles.out.txt"));
        err=new PrintStream(new FileOutputStream(testPath+"/autoGen.libFiles.err.txt"));
        if(files!=null){
            for(File file:files){
                String path=file.getAbsolutePath();
                if(path.endsWith(Interpreter.DEFAULT_FILE_EXTENSION)){
                    String name = path.substring(Interpreter.libPath.length(),
                            path.length() - Interpreter.DEFAULT_FILE_EXTENSION.length());
                    out.println(name+":");
                    err.println(name+":");
                    includeAll.write(name+" #include");
                    includeAll.newLine();
                    context=new Interpreter.IOContext(System.in,out,err);
                    Interpreter.compileAndRun(path,new String[]{System.getProperty("user.dir")},context);
                }else if(!file.getName().equals("native.jar")){//ignore native code extensions
                    System.err.println("non-concat lib file:"+path);
                }
            }
        }
        includeAll.write("main proc( => ){ }");
        includeAll.flush();

        File tests=new File(testPath);
        files=tests.listFiles();
        if(files!=null){
            for(File file:files){
                String path=file.getAbsolutePath();
                if(path.endsWith(Interpreter.DEFAULT_FILE_EXTENSION)){
                    String reducedPath = path.substring(0, path.length() - Interpreter.DEFAULT_FILE_EXTENSION.length());
                    out=new PrintStream(new FileOutputStream(reducedPath +".out.txt"));
                    err=new PrintStream(new FileOutputStream(reducedPath +".err.txt"));
                    context=new Interpreter.IOContext(System.in,out,err);
                    Interpreter.compileAndRun(path,new String[]{System.getProperty("user.dir")},context);
                }
            }
        }else{
            throw new IOException("unable to list files");
        }
    }
}
