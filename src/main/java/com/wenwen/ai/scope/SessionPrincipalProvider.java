package com.wenwen.ai.scope;

import com.wenwen.ai.query.CohortException;
import javax.servlet.http.HttpSession;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Consumes only the server-owned typed session attribute; never creates a session. */
public final class SessionPrincipalProvider implements PrincipalProvider {
    public TrustedDoctor requireCurrent(){
        try{
            org.springframework.web.context.request.RequestAttributes attributes=RequestContextHolder.getRequestAttributes();
            if(attributes instanceof ServletRequestAttributes){
                HttpSession session=((ServletRequestAttributes)attributes).getRequest().getSession(false);
                Object identity=session==null?null:session.getAttribute(AuthenticatedDoctorSession.ATTRIBUTE);
                if(identity instanceof AuthenticatedDoctorSession)return ((AuthenticatedDoctorSession)identity).getDoctor();
            }
        }catch(IllegalStateException expired){/* invalidated session */}
        throw new CohortException(401,"UNAUTHENTICATED","请先登录");
    }
}
