package com.pgcrm.security;

import com.pgcrm.entity.User;
import com.pgcrm.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String token = extractToken(request);
        System.out.println("JwtAuthenticationFilter: Path=" + path + ", Token present=" + (token != null));

        if (token != null) {
            try {
                if (jwtUtil.isTokenValid(token)) {
                    Claims claims = jwtUtil.extractAllClaims(token);
                    String userId = claims.getSubject();
                    String role = claims.get("role", String.class);
                    String branchId = claims.get("branch_id", String.class);
                    System.out.println("JwtAuthenticationFilter: Token valid. User=" + userId + ", Role=" + role);

                    String selectedBranchId = request.getHeader("X-Selected-Branch-Id");
                    String activeBranchId = null;
                    if (branchId != null) {
                        String[] allowedBranches = branchId.split(",");
                        if (selectedBranchId != null && java.util.Arrays.asList(allowedBranches).contains(selectedBranchId)) {
                            activeBranchId = selectedBranchId;
                        } else {
                            activeBranchId = allowedBranches[0];
                        }
                    }

                    if (activeBranchId != null) {
                        request.setAttribute("branchId", activeBranchId);
                    }

                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                    var auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } else {
                    System.out.println("JwtAuthenticationFilter: jwtUtil.isTokenValid returned false");
                }
            } catch (Exception e) {
                System.out.println("JwtAuthenticationFilter: Exception validating token: " + e.getMessage());
                e.printStackTrace();
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
