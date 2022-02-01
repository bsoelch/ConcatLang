public class math{
    public static double nativeImpl_exp(double x){
        return Math.exp(x);
    }
    public static double nativeImpl_log(double x){
        return Math.log(x);
    }
    public static double nativeImpl_sin(double x){
        return Math.sin(x);
    }
    public static double nativeImpl_cos(double x){
        return Math.cos(x);
    }
    public static double nativeImpl_tan(double x){
        return Math.tan(x);
    }

    public static double nativeImpl_floor(double x){
        return Math.floor(x);
    }
    public static double nativeImpl_ceil(double x){
        return Math.ceil(x);
    }
    public static double nativeImpl_round(double x){
        return Math.round(x);
    }

    public static double nativeImpl_pow(double x,double e){
        return Math.pow(x,e);
    }

    public static Object[] nativeImpl_floatToBytes(double x){
        long l=Double.doubleToRawLongBits(x);
        byte[] bytes=new byte[8];
        for(int i=0;i<8;i++){
            bytes[i]=(byte)((l>>>8*i)&0xff);
        }
        return new Object[]{bytes,0,8,8};
    }
    public static double nativeImpl_bytesToFloat(Object[] bytes){
        long l=0;
        for(int i=0;i<(int)bytes[2];i++){
            l|=(((byte[])bytes[0])[i+(int)bytes[1]]&0xffL)<<8*i;
        }
        return Double.longBitsToDouble(l);
    }


}