package bsoelch.concat;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class Test {//TODO add a new test for array access operations

    public static void main(String[] args) throws IOException {
        //TODO add tests for all internal procedures
        String testPath=System.getProperty("user.dir")+"/tests/";
        //auto generated tests.
        //test including library files (each separately and all at once)
        File lib=new File(Parser.libPath);
        HashMap<String, Parser.Program> libraryFiles=new HashMap<>();
        File[] files=lib.listFiles();
        PrintStream out;
        PrintStream err;
        IOContext context;
        BufferedWriter includeAll = new BufferedWriter(new FileWriter(testPath+"autoGen.includeAll.concat"));
        includeAll.write("test/includeAll :");
        includeAll.newLine();
        out=new PrintStream(new FileOutputStream(testPath+"/autoGen.libFiles.out.txt"));
        err=new PrintStream(new FileOutputStream(testPath+"/autoGen.libFiles.err.txt"));
        if(files!=null){
            for(File file:files){
                String path=file.getAbsolutePath();
                if(path.endsWith(Parser.DEFAULT_FILE_EXTENSION)){
                    String name = path.substring(Parser.libPath.length(),
                            path.length() - Parser.DEFAULT_FILE_EXTENSION.length());
                    out.println(name+":");
                    err.println(name+":");
                    includeAll.write(name+" #include");
                    includeAll.newLine();
                    context=new IOContext(System.in,out,err);
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
            for(Map.Entry<String, Parser.Program> libFile:libraryFiles.entrySet()){
                File file=new File(testPath+"lib/"+libFile.getKey());
                String path=file.getAbsolutePath();
                if(file.exists()){
                    String reducedPath = path.substring(0, path.length() - Parser.DEFAULT_FILE_EXTENSION.length());
                    out=new PrintStream(new FileOutputStream(reducedPath +".out.txt"));
                    err=new PrintStream(new FileOutputStream(reducedPath +".err.txt"));
                    context=new IOContext(System.in,out,err);
                    Parser.Program testP =
                            Interpreter.compileAndRun(path, new String[]{System.getProperty("user.dir")}, context);
                    if(testP!=null){
                        if(libFile.getValue()==null){
                            System.err.println("unable to build library file: "+file.getAbsolutePath());
                            continue;
                        }
                        Parser.RootContext testC= testP.rootContext();
                        for(Map.Entry<String, Parser.Declareable> d:libFile.getValue().rootContext().declareables()){
                            if(d.getValue() instanceof OverloadedProcedure){
                                for(Parser.Callable c:((OverloadedProcedure) d.getValue()).procedures){
                                    if(c.declaredAt().path.endsWith(libFile.getKey())){
                                        Parser.Declareable dTest=testC.getElement(d.getKey(),false);
                                        if(dTest==null){
                                            System.err.println("declareable \""+d.getKey()+"\" at "+c.declaredAt()+
                                                    " is not used in test for library file: \""+libFile.getKey()+"\"");
                                        }else if(dTest instanceof OverloadedProcedure){
                                            for(Parser.Callable cTest:((OverloadedProcedure) dTest).procedures){
                                                if(cTest.type().equals(c.type())&&cTest.unused()){
                                                    System.err.println("procedure \""+d.getKey()+"\" at "+cTest.declaredAt()+
                                                            " is not used in test for library file: \""+libFile.getKey()+"\"");
                                                }
                                            }
                                        }else if(dTest.unused()){
                                            System.err.println(Parser.declarableName(dTest.declarableType(),false)+" \""+d.getKey()+
                                                    "\" at "+c.declaredAt()+
                                                    " is not used in test for library file: \""+libFile.getKey()+"\"");
                                        }
                                    }
                                }
                            }else if(d.getValue().declaredAt().path.endsWith(libFile.getKey())){
                                Parser.Declareable dTest=testC.getElement(d.getKey(),false);
                                if(dTest==null){
                                    System.err.println("declareable \""+d.getKey()+"\" at "+d.getValue().declaredAt()+
                                            " is not used in test for library file: \""+libFile.getKey()+"\"");
                                }else if(dTest instanceof OverloadedProcedure){
                                    for(Parser.Callable c:((OverloadedProcedure) dTest).procedures){
                                        if(c.unused()){
                                            System.err.println("procedure \""+d.getKey()+"\" at "+c.declaredAt()+
                                                    " is not used in test for library file: \""+libFile.getKey()+"\"");
                                        }
                                    }
                                }else if(dTest.unused()){
                                    System.err.println(Parser.declarableName(dTest.declarableType(),false)+" \""+d.getKey()+
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
                if(path.endsWith(Parser.DEFAULT_FILE_EXTENSION)){
                    String reducedPath = path.substring(0, path.length() - Parser.DEFAULT_FILE_EXTENSION.length());
                    out=new PrintStream(new FileOutputStream(reducedPath +".out.txt"));
                    err=new PrintStream(new FileOutputStream(reducedPath +".err.txt"));
                    context=new IOContext(System.in,out,err);
                    Interpreter.compileAndRun(path,new String[]{System.getProperty("user.dir")},context);
                }
            }
        }else{
            throw new IOException("unable to list files");
        }
    }
}
