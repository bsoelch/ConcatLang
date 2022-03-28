package bsoelch.concat;

enum Mutability {
    DEFAULT, MUTABLE, IMMUTABLE, UNDECIDED, INHERIT;

    static boolean isEqual(Mutability m1,Mutability m2){
        return m1==m2||(m1==DEFAULT&&m2==IMMUTABLE)||(m2==DEFAULT&&m1==IMMUTABLE);
    }
    static boolean isDifferent(Mutability m1,Mutability m2){
        return m1!=m2&&(m1!=DEFAULT||m2!=IMMUTABLE)&&(m1!=IMMUTABLE||m2!=DEFAULT);
    }
}
