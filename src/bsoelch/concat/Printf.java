package bsoelch.concat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Consumer;

public class Printf {
    private Printf(){}
    private static final int PRINTF_FLAG_PAD_0=1, PRINTF_FLAG_ALIGN_LEFT =2,PRINTF_FLAG_SGN=4, PRINTF_FLAG_SGN_SPACE =8;

    static char digit(long value,int base,boolean big){
        if(value<0||value>=base)
            throw new IllegalArgumentException("Digit out of range:"+value+" base:"+base);
        if(value<10){
            return (char)(value+'0');
        }else if(value<36){
            return (char)(value-10+((big||base>36)?'A':'a'));
        }else{
            return (char)(value-36+'a');
        }
    }
    static String toString(boolean unsigned,long val,int base,boolean big,char plus_Sgn){
        if(val==0){
            return (plus_Sgn!=0)?plus_Sgn+"0":"0";
        }
        StringBuilder res=new StringBuilder();
        boolean neg=false;
        if(val<0){
            if(unsigned){
                res.append(digit(Long.remainderUnsigned(val,base),base,big));
                val=Long.divideUnsigned(val,base);
            }else{
                neg=true;
                val=-val;
            }
        }
        while(val>0){
            res.append(digit(val%base,base,big));
            val/=base;
        }
        if(neg){
            res.append('-');
        }else if(plus_Sgn!=0){
            res.append(plus_Sgn);
        }
        return res.reverse().toString();
    }
    static String toString(double val, int precision, int base, boolean big,boolean sci,char plusSgn){
        if(base<2||base>62){
            throw new IllegalArgumentException("base has to be between 2 and 62");
        }
        StringBuilder res=new StringBuilder();
        if(val<0){
            res.append('-');
            val=-val;
        }else if(plusSgn!=0){
            res.append(plusSgn);
        }
        double eps=Math.ulp(val);
        int e=(int)Math.floor(Math.log(val)/Math.log(base));
        int e_max=e;
        double p=Math.pow(base,e);
        int[] buff=new int[64];
        int n=0;
        while(p>eps){
            buff[n++]=(int)(val/p);
            val%=p;
            p/=base;
            e--;
        }
        n=(precision==0||precision>=n)?n-1:precision;
        if(buff[n]>=base/2){
            int j=n-1;
            boolean inc=true;
            while(inc&&j>=0){
                buff[j]++;
                if(buff[j]>=base){
                    buff[j]-=base;
                }else{
                    inc=false;
                }
                j--;
            }
            if(inc){
                System.arraycopy(buff,0,buff,1,n);
                buff[0]=1;
                e_max++;
                n++;
            }
        }
        while(n-1>e_max&&buff[n-1]==0){
            n--;
        }
        if(sci||e_max>=n||e_max<-5){
            while(n-1>0&&buff[n-1]==0){
                n--;
            }
            res.append(digit(buff[0],base,big));
            if(n>1){
                res.append('.');
                for(int i=1;i<n;i++){
                    res.append(digit(buff[i],base,big));
                }
            }
            res.append(base<=10?'E':base<=30?'X':'#');
            res.append(toString(false,e_max,base,big,'+'));
        }else if(e_max>=0){
            for(int i=0;i<n;i++){
                res.append(digit(buff[i],base,big));
                if(e_max==i&&i<n-1){
                    res.append('.');
                }
            }
        }else{
            res.append("0.");
            res.append("0".repeat(-e_max-1));
            for(int i=0;i<n;i++){
                res.append(digit(buff[i],base,big));
            }
        }
        return res.toString();
    }


    private interface PrintfFormat {
        String value(Value[] args) throws ConcatRuntimeError;
    }
    private record StringFormat(String value) implements PrintfFormat {
        @Override
        public String value(Value[] args) {
            return value;
        }
    }
    private record FormatFormat(int index,int flags,int w,boolean w_ptr,int p,boolean p_ptr,int b,boolean b_ptr,char format) implements PrintfFormat {
        @Override
        public String value(Value[] args) throws ConcatRuntimeError {
            boolean alignLeft = (flags & PRINTF_FLAG_ALIGN_LEFT) != 0;
            int width=w_ptr?(int)args[w].asLong():w;
            if(width<0){//negative precision => alignment on left side
                alignLeft=!alignLeft;
                width=-width;
            }
            int precision=p_ptr?(int)args[p].asLong():p;
            if(precision<0){//0 -> default
                throw new ConcatRuntimeError("printf: precision has to be at least 1");
            }
            int base=b_ptr?(int)args[b].asLong():b;
            String raw;
            char plusSgn=0;
            if((flags&PRINTF_FLAG_SGN)!=0){
                plusSgn='+';
            }else if((flags&PRINTF_FLAG_SGN_SPACE)!=0){
                plusSgn=' ';
            }
            switch (format){
                case 'b'-> raw=""+args[index].asBool();
                case 'B'-> raw=(""+args[index].asBool()).toUpperCase(Locale.ROOT);
                case 'i','I'-> raw=Printf.toString(false,args[index].asLong(),base==0?10:base,format=='I',plusSgn);
                case 'u','U'-> raw=Printf.toString(true,args[index].asLong(),base==0?10:base,format=='U',plusSgn);
                case 'd'-> raw=Printf.toString(false,args[index].asLong(),10,false,plusSgn);
                case 'x', 'X'-> raw=Printf.toString(true,args[index].asLong(),16,format=='X',plusSgn);
                case 'e','E' -> raw=Printf.toString(args[index].asDouble(),precision,base==0?10:base,format=='E',true,plusSgn);
                case 'f','F' -> raw=Printf.toString(args[index].asDouble(),precision,base==0?10:base,format=='F',false,plusSgn);
                case 'c'-> raw=String.valueOf(Character.toChars(args[index].asChar()));
                case 's','S'-> raw=args[index].stringValue(precision,base,format=='S',plusSgn);
                case '%' -> raw="%";
                default -> throw new IllegalArgumentException(format+" is no valid format specifier");
            }
            if(raw.length()<width){
                char[] padding=new char[width-raw.length()];
                Arrays.fill(padding,((flags&PRINTF_FLAG_PAD_0)!=0)?'0':' ');
                if(alignLeft){
                    raw=raw+String.valueOf(padding);
                }else{
                    raw=String.valueOf(padding)+raw;
                }
            }
            return raw;
        }
    }
    private static boolean isFormatChar(char c){
        switch (c){
            case 'b','B','i','I','u','U','d','x','X','f','F','e','E','c','s','S','%' -> {
                return true;
            }
            default -> {return false;}
        }
    }
    /*custom printf, since Java internal printf functions don't specify the number of required arguments*/
    public static void printf(String format, ArrayDeque<Value> stack, Consumer<String> out) throws ConcatRuntimeError {
        ArrayList<PrintfFormat> parts=new ArrayList<>();
        int i0=0,i,n=format.length();
        int count=0;
        int maxFormat=-1;
        StringBuilder tmp=new StringBuilder();
        while(i0<n){
            i=format.indexOf('%',i0);
            if(i<0){
                parts.add(new StringFormat(format.substring(i0)));
                i0=n;
            }else{
                parts.add(new StringFormat(format.substring(i0,i)));
                i0=++i;
                while(i0<format.length()&&!isFormatChar(format.charAt(i0))){
                    i0++;
                }
                if(i0>=format.length()){
                    throw new ConcatRuntimeError("printf: unfinished or invalid format String: " + format.substring(i));
                }
                //format-type
                char formatChar=format.charAt(i0);
                String formatString=format.substring(i,i0++);
                if(formatChar=='%'){
                    if(formatString.length()>0){
                        throw new ConcatRuntimeError("printf: invalid Format String:" + formatString + formatChar+
                                " format '%' does not allow any additional parameters");
                    }
                    parts.add(new StringFormat("%"));
                }else {
                    int index = count++;
                    int flags = 0;
                    int w = 0;
                    boolean w_ptr = false;
                    int p = 0;
                    boolean p_ptr = false;
                    int b = 0;
                    boolean b_ptr = false;
                    i = 0;
                    tmp.setLength(0);
                    //([0-9]+$)?  index
                    while (i < formatString.length() && '0' <= formatString.charAt(i) && formatString.charAt(i) <= '9') {
                        tmp.append(formatString.charAt(i++));
                    }
                    if (i < formatString.length() && formatString.charAt(i) == '$') {
                        index = Integer.parseInt(tmp.toString());
                        tmp.setLength(0);
                    }
                    //([-+ 0]+)? flags
                    //'-'  The result will be left-justified.
                    //'+'  The result will always include a sign
                    //' '  The result will include a leading space for positive values
                    //'0'  The result will be zero-padded
                    int i1 = 0;
                    while (i1<tmp.length()&&tmp.charAt(i1) == '0') {
                        flags |= PRINTF_FLAG_PAD_0;
                        i1++;
                    }
                    tmp.replace(0, i1, "");
                    if (tmp.length() == 0) {
                        flagLoop:
                        while (i < formatString.length()) {
                            switch (formatString.charAt(i)) {
                                case '-' -> {
                                    flags |= PRINTF_FLAG_ALIGN_LEFT;
                                    i++;
                                }
                                case '+' -> {
                                    flags |= PRINTF_FLAG_SGN;
                                    i++;
                                }
                                case ' ' -> {
                                    flags |= PRINTF_FLAG_SGN_SPACE;
                                    i++;
                                }
                                case '0' -> {
                                    flags |= PRINTF_FLAG_PAD_0;
                                    i++;
                                }
                                default -> {
                                    break flagLoop;
                                }
                            }
                        }
                        while (i < formatString.length() && '0' <= formatString.charAt(i) && formatString.charAt(i) <= '9') {
                            tmp.append(formatString.charAt(i++));
                        }
                    }
                    //([0-9]+)? width
                    if (tmp.length() > 0) {
                        w = Integer.parseInt(tmp.toString());
                        tmp.setLength(0);
                    } else if (i < formatString.length() && formatString.charAt(i) == '*') {//* (...$)
                        i++;
                        w_ptr = true;
                        w = count++;
                        while (i < formatString.length() && '0' <= formatString.charAt(i) && formatString.charAt(i) <= '9') {
                            tmp.append(formatString.charAt(i++));
                        }
                        if(!tmp.isEmpty()) {
                            w = Integer.parseInt(tmp.toString());
                            tmp.setLength(0);
                        }
                    }
                    //(.[0-9]+)? precision
                    if (i < formatString.length() && formatString.charAt(i) == '.') {
                        switch (Character.toLowerCase(formatChar)) {
                            case 'f','e','s' -> {
                            }
                            default -> throw new ConcatRuntimeError("printf: invalid Format String:" + formatString + formatChar +
                                    " precision-parameter only allowed for formats f, e and s");
                        }
                        i++;
                        if (formatString.charAt(i) == '*') {
                            i++;
                            p_ptr = true;
                            p = count++;
                        }
                        while (i < formatString.length() && '0' <= formatString.charAt(i) && formatString.charAt(i) <= '9') {
                            tmp.append(formatString.charAt(i++));
                        }
                        if(!(tmp.isEmpty()&&p_ptr)){
                            p = Integer.parseInt(tmp.toString());
                            tmp.setLength(0);
                        }
                    }
                    //'('[0-9]+')' base
                    if (i < formatString.length() && formatString.charAt(i) == '(') {
                        switch (Character.toLowerCase(formatChar)) {
                            case 'i','u','f','e','s' -> {
                            }
                            default -> throw new ConcatRuntimeError("printf: invalid Format String:" + formatString + formatChar +
                                    " base-parameter only allowed for formats i,u,f,e and s");
                        }
                        i++;
                        if (i < formatString.length() && formatString.charAt(i) == '*') {
                            b_ptr = true;
                            i++;
                            b = count++;
                        }
                        while (i < formatString.length() && '0' <= formatString.charAt(i) && formatString.charAt(i) <= '9') {
                            tmp.append(formatString.charAt(i++));
                        }
                        if (i < formatString.length() &&formatString.charAt(i) == ')') {
                            if(!(tmp.isEmpty()&&b_ptr)){
                                b = Integer.parseInt(tmp.toString());
                                tmp.setLength(0);
                            }
                            i++;
                        }else{
                            throw new ConcatRuntimeError("printf: binvalid Format String:" + formatString + formatChar);
                        }
                    }
                    if (i < formatString.length()||tmp.length()>0) {
                        throw new ConcatRuntimeError("printf: invalid Format String:" + formatString + formatChar);
                    }
                    parts.add(new FormatFormat(index,flags,w,w_ptr,p,p_ptr,b,b_ptr,formatChar));
                    maxFormat=Math.max(maxFormat, index);
                    if(w_ptr)
                        maxFormat=Math.max(maxFormat, w);
                    if (p_ptr)
                        maxFormat=Math.max(maxFormat, p);
                    if (b_ptr)
                        maxFormat=Math.max(maxFormat, b);
                }
            }
        }
        Value[] args=new Value[maxFormat+1];
        for(i=1;i<=args.length;i++){
            args[args.length-i]=Interpreter.pop(stack);
        }
        for(PrintfFormat f:parts){
            out.accept(f.value(args));
        }
    }



}
