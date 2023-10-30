import { BaseService } from "../baseService";
import { UserInfo } from "../../models";

/**
 * A service that provides access to the /users endpoint.
 */
export class UsersService extends BaseService {

    private currentUserInfo: UserInfo;

    constructor() {
        super();
        this.currentUserInfo = {
            username: "",
            displayName: "",
            admin: false,
            developer: false,
            viewer: false
        };
    }

    public init(): void {
        // Nothing to init (done in c'tor)
    }

    public currentUser(): UserInfo {
        return this.currentUserInfo;
    }

    public updateCurrentUser(): Promise<UserInfo> {
        if (this.auth?.isAuthenticated()) {
            // TODO cache the response for a few minutes to limit the # of times this is called per minute??
            const endpoint: string = this.endpoint("/v2/users/me");
            return this.httpGet<UserInfo>(endpoint).then(userInfo => {
                this.currentUserInfo = userInfo;
                return userInfo;
            });
        } else {
            return Promise.resolve(this.currentUserInfo);
        }
    }

}
