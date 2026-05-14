export class ArrayUtils {

    /**
     * Returns the intersection of two arrays.
     */
    static intersect(a1: any[], a2: any[]): any[] {
        const rval: any[] = [];
        for (const item of a1) {
            if (ArrayUtils.contains(a2, item)) {
                rval.push(item);
            }
        }
        return rval;
    }

    /**
     * Returns true if the given item is contained in the given array.
     */
    static contains(a: any[], item: any): boolean {
        for (const aitem of a) {
            if (aitem === item) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tests whether two arrays are the same.
     */
    static equals(a1: any[], a2: any[]): boolean {
        if (a1 === a2) {
            return true;
        }
        if (a1 === null || a2 === null || a1 === undefined || a2 === undefined) {
            return false;
        }
        if (a1.length !== a2.length) {
            return false;
        }
        for (let i = 0; i < a1.length; i++) {
            if (a1[i] !== a2[i]) {
                return false;
            }
        }
        return true;
    }
}
