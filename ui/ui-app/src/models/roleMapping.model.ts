export class RoleTypes {
    public static READ_ONLY: string = "READ_ONLY";
    public static DEVELOPER: string = "DEVELOPER";
    public static ADMIN: string = "ADMIN";
}


export interface RoleMapping {

    principalId: string;
    role: string;
    principalName: string;

}
