package com.bitmall.agent.knowledge.model;

/** embedding 值的共同校验；数据库适配与模型适配都遵守相同的维度/有限数/非零约束。 */
public final class KnowledgeVectors {
  private KnowledgeVectors() {}

  public static void validate(double[] vector, int dimension) {
    if (vector == null || vector.length != dimension)
      throw new IllegalStateException("embedding 维度不符");
    double norm = 0;
    for (double v : vector) {
      if (!Double.isFinite(v)) throw new IllegalStateException("embedding 非有限数值");
      norm += v * v;
    }
    if (norm == 0 || !Double.isFinite(norm)) throw new IllegalStateException("embedding 范数无效");
  }
}
