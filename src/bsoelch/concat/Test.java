package bsoelch.concat;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class Test {

    public static void main(String[] args) throws IOException {
        //TODO add tests for all internal procedures
        String testPath=System.getProperty("user.dir")+"/tests/";
        //auto generated tests.
        //test including library files (each separately and all at once)
        File lib=new File(Interpreter.libPath);
        HashMap<String, Interpreter.Program> libraryFiles=new HashMap<>();
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
                    libraryFiles.put(file.getName(),
                            Interpreter.compileAndRun(path,new String[]{System.getProperty("user.dir")},context));
                }else if(!file.getName().equals("native.jar")){//ignore native code extensions
                    System.err.println("non-concat lib file:"+path);
                }
            }
        }
        includeAll.write("main public proc( => ){ }");
        includeAll.flush();

        //run lib-tests
        if(files!=null){
            for(Map.Entry<String, Interpreter.Program> libFile:libraryFiles.entrySet()){
                File file=new File(testPath+"lib/"+libFile.getKey());
                String path=file.getAbsolutePath();
                if(file.exists()){
                    String reducedPath = path.substring(0, path.length() - Interpreter.DEFAULT_FILE_EXTENSION.length());
                    out=new PrintStream(new FileOutputStream(reducedPath +".out.txt"));
                    err=new PrintStream(new FileOutputStream(reducedPath +".err.txt"));
                    context=new Interpreter.IOContext(System.in,out,err);
                    Interpreter.Program testP =
                            Interpreter.compileAndRun(path, new String[]{System.getProperty("user.dir")}, context);
                    if(testP!=null){
                        Interpreter.RootContext testC=
                                testP.rootContext();
                        for(Map.Entry<String, Interpreter.Declareable> d:libFile.getValue().rootContext().declareables()){
                            if(d.getValue().declaredAt().path.endsWith(libFile.getKey())){
                                Interpreter.Declareable dTest=testC.getElement(d.getKey(),false);
                                if(dTest==null){
                                    System.err.println("declareable \""+d.getKey()+"\" at "+d.getValue().declaredAt()+
                                            " is not used in test for library file: \""+libFile.getKey()+"\"");
                                }else if(dTest instanceof OverloadedProcedure){
                                    for(Interpreter.Callable c:((OverloadedProcedure) dTest).procedures){
                                        if(c.unused()){
                                            System.err.println("procedure \""+d.getKey()+"\" at "+c.declaredAt()+
                                                    " is not used in test for library file: \""+libFile.getKey()+"\"");
                                        }
                                    }
                                }else if(dTest.unused()){
                                    System.err.println(Interpreter.declarableName(dTest.declarableType(),false)+" \""+d.getKey()+
                                            "\" at "+d.getValue().declaredAt()+
                                            " is not used in test for library file: \""+libFile.getKey()+"\"");
                                }
                            }
                        }
                    }
                }else{
                    System.err.println("No test found for library file: \""+libFile.getKey()+"\"");
                }
            }
        }else{
            throw new IOException("unable to list files");
        }
        //run tests
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
