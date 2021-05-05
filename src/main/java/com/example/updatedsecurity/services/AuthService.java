package com.example.updatedsecurity.services;

import com.example.updatedsecurity.Dto.UserAuthDTO;
import com.example.updatedsecurity.Dto.UserAuthDTOResultTransformer;
import com.example.updatedsecurity.enums.Role;
import com.example.updatedsecurity.inpDTO.GenInp;
import com.example.updatedsecurity.inpDTO.LogInInp;
import com.example.updatedsecurity.inpDTO.RegisterInp;
import com.example.updatedsecurity.model.Permission;
import com.example.updatedsecurity.model.User;
import com.example.updatedsecurity.repositories.PermissionRepository;
import com.example.updatedsecurity.repositories.UserRepository;
import com.example.updatedsecurity.security.JWTUtility;
import com.example.updatedsecurity.security.UserPrincipal;
import com.example.updatedsecurity.security.UserPrincipalDetailsService;
import org.hibernate.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.persistence.EntityManagerFactory;
import java.util.List;
import java.util.UUID;


@Service
public class AuthService {
    private UserRepository userRepository;
    private AuthenticationManager authenticationManager;
    private PasswordEncoder encoder;
    private JWTUtility jwtUtility;
    private UserPrincipalDetailsService userPrincipalDetailsService;
    private PermissionRepository permissionRepository;
    private final EntityManagerFactory entityManagerFactory;



    public AuthService(UserRepository userRepository,
                       AuthenticationManager authenticationManager,
                       PasswordEncoder encoder, JWTUtility jwtUtility,
                       UserPrincipalDetailsService userPrincipalDetailsService,
                       PermissionRepository permissionRepository,
                       EntityManagerFactory entityManagerFactory) {
        this.userRepository = userRepository;
        this.authenticationManager = authenticationManager;
        this.encoder = encoder;
        this.jwtUtility = jwtUtility;
        this.userPrincipalDetailsService = userPrincipalDetailsService;
        this.permissionRepository = permissionRepository;
        this.entityManagerFactory = entityManagerFactory;
    }

    public List<UserAuthDTO> authDetails(){

            var entityManager = entityManagerFactory.createEntityManager();
            var users =  entityManager.createNativeQuery(" " +
                    "select  " +
                    "u.email as u_email," +
                    "u.name as u_name, " +
                    "u.role as u_role," +
                    "p.code as p_code " +
                    "from users u " +
                    "join user_permissions up on u.id = up.user_id " +
                    "join permission p on up.permission_id = p.id ")
                    .unwrap(Query.class)
                    .setResultTransformer(new UserAuthDTOResultTransformer())
                    .getResultList();

            entityManager.close();
            return users;


    }
    public List<UserAuthDTO> authDetailsById(String idStr){
        try {
            var id = UUID.fromString(idStr);
            var entityManager = entityManagerFactory.createEntityManager();
            var users =  entityManager.createNativeQuery(" " +
                    "select  " +
                    "u.email as u_email," +
                    "u.name as u_name, " +
                    "u.role as u_role," +
                    "p.code as p_code " +
                    "from users u " +
                    "join user_permissions up on u.id = up.user_id " +
                    "join permission p on up.permission_id = p.id " +
                    "where u.id = :userId")
                    .setParameter("userId", id)
                    .unwrap(Query.class)
                    .setResultTransformer(new UserAuthDTOResultTransformer())
                    .getResultList();

            entityManager.close();
            return users;

        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid id");
        }
    }


    public String register(RegisterInp registerInp) {
        var newUser = new User(registerInp.getName(), registerInp.getEmail(),
                encoder.encode(registerInp.getPassword()));

        switch (registerInp.getRole()){
            case "admin":
                newUser.setRole(Role.ADMIN);
                break;
            case "manager":
                newUser.setRole(Role.MANAGER);
                break;
            default:
                newUser.setRole(Role.USER);
        }
        userRepository.save(newUser);
        return "registered";
    }

    public String LogIn(LogInInp logInInp) throws Exception {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            logInInp.getEmail(),
                            logInInp.getPassword()
                    )
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);

            final UserPrincipal userDetails = (UserPrincipal) authentication.getPrincipal();

            return jwtUtility.generateToken(userDetails);


        } catch (BadCredentialsException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid credentials");
        }
    }

    public void createPermission(String code){
        if (!permissionRepository.existsPermissionByCode(code)){
            var newPerm = new Permission(code);
            permissionRepository.save(newPerm);
        }
        else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "perm there");
        }
    }


    public String addPermissionToUser(String userName, GenInp genInp){
        var user = userRepository.findUserByName(userName).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "user not there")
        );

        for (String code: genInp.getStringList()) {
           permissionRepository.findPermissionByCode(code)
                   .ifPresent(permission -> user.addPermission(permission));
        }
        userRepository.save(user);
        return "done";
    }

}
