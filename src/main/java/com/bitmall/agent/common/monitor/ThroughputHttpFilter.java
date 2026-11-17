package com.bitmall.agent.common.monitor;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** HTTP 处理完成时记入当前自然秒；只统计商品详情和正式提交，不混入发号、监控和前端轮询。 */
@Component
@ConditionalOnProperty(name = "app.throughput.enabled", havingValue = "true")
public class ThroughputHttpFilter extends OncePerRequestFilter {
  private final OneSecondThroughput metrics;

  public ThroughputHttpFilter(OneSecondThroughput metrics) {
    this.metrics = metrics;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !(isProductQuery(request)
        || ("POST".equals(request.getMethod())
            && "/api/store/orders/submit".equals(request.getRequestURI())));
  }

  private boolean isProductQuery(HttpServletRequest request) {
    return "GET".equals(request.getMethod())
        && request.getRequestURI().startsWith("/api/store/products/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    int escapedError = 0;
    try {
      chain.doFilter(request, response);
    } catch (ServletException | IOException | RuntimeException ex) {
      escapedError = 500;
      throw ex;
    } finally {
      metrics.httpCompleted(
          isProductQuery(request), escapedError == 0 ? response.getStatus() : escapedError);
    }
  }
}
