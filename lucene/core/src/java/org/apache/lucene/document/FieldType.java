/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.document;

import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer; // javadocs
import org.apache.lucene.index.DocValuesType;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexableFieldType;
import org.apache.lucene.index.PointValues;
import org.apache.lucene.index.VectorValues;

/** Describes the properties of a field. */
public class FieldType implements IndexableFieldType {

  /**
   * 代表是否需要保存该字段，如果为false，则lucene不会保存这个字段的值，而搜索结果中返回的文档只会包含保存了的字段。
   */
  private boolean stored;
  /**
   *  代表是否做分词，在lucene中只有TextField这一个字段需要做分词。
   */
  private boolean tokenized = true;
  /**
   * http://makble.com/what-is-term-vector-in-lucene
   *
   * termVector: 这篇文章很好的解释了term vector的概念，简单来说，term vector保存了
   * 一个文档内所有的term的相关信息，包括Term值、出现次数（frequencies）以及位置（positions）等，
   * 是一个per-document inverted index，提供了根据docid来查找该文档内所有term信息的能力。
   * 对于长度较小的字段不建议开启term verctor，因为只需要重新做一遍分词即可拿到term信息，
   * 而针对长度较长或者分词代价较大的字段，则建议开启term vector。Term vector的用途主要有两个，
   * 一是关键词高亮，二是做文档间的相似度匹配（more-like-this）。
   */
  private boolean storeTermVectors;
  private boolean storeTermVectorOffsets;
  private boolean storeTermVectorPositions;
  private boolean storeTermVectorPayloads;
  /**
   * Norms是normalization的缩写，lucene允许每个文档的每个字段都存储一个normalization factor，
   * 是和搜索时的相关性计算有关的一个系数。Norms的存储只占一个字节，但是每个文档的每个字段都会独立存储一份，
   * 且Norms数据会全部加载到内存。所以若开启了Norms，会消耗额外的存储空间和内存。但若关闭了Norms，
   * 则无法做index-time boosting（elasticsearch官方建议使用query-time boosting来替代）以及length
   * normalization。
   */
  private boolean omitNorms;
  /**
   * Lucene提供倒排索引的5种可选参数（NONE、DOCS、DOCS_AND_FREQS、DOCS_AND_FREQS_AND_POSITIONS、
   * DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS），用于选择该字段是否需要被索引，以及索引哪些内容。
   */
  private IndexOptions indexOptions = IndexOptions.NONE;
  private boolean frozen;
  /**
   * DocValue是Lucene 4.0引入的一个正向索引（docid到field的一个列存），大大优化了sorting、
   * faceting或aggregation的效率。DocValues是一个强schema的存储结构，开启DocValues的字段必须
   * 拥有严格一致的类型，目前Lucene只提供NUMERIC、BINARY、SORTED、SORTED_NUMERIC和SORTED_SET五种类型。
   */
  private DocValuesType docValuesType = DocValuesType.NONE;
  /**
   * ucene支持多维数据的索引，采取特殊的索引来优化对多维数据的查询，这类数据最典型的应用场景是地理位置索引
   * ，一般经纬度数据会采取这个索引方式。
   */
  private int dimensionCount;
  private int indexDimensionCount;
  private int dimensionNumBytes;
  private int vectorDimension;
  private VectorValues.SimilarityFunction vectorSimilarityFunction =
      VectorValues.SimilarityFunction.NONE;
  private Map<String, String> attributes;

  /** Create a new mutable FieldType with all of the properties from <code>ref</code> */
  public FieldType(IndexableFieldType ref) {
    this.stored = ref.stored();
    this.tokenized = ref.tokenized();
    this.storeTermVectors = ref.storeTermVectors();
    this.storeTermVectorOffsets = ref.storeTermVectorOffsets();
    this.storeTermVectorPositions = ref.storeTermVectorPositions();
    this.storeTermVectorPayloads = ref.storeTermVectorPayloads();
    this.omitNorms = ref.omitNorms();
    this.indexOptions = ref.indexOptions();
    this.docValuesType = ref.docValuesType();
    this.dimensionCount = ref.pointDimensionCount();
    this.indexDimensionCount = ref.pointIndexDimensionCount();
    this.dimensionNumBytes = ref.pointNumBytes();
    this.vectorDimension = ref.vectorDimension();
    this.vectorSimilarityFunction = ref.vectorSimilarityFunction();
    if (ref.getAttributes() != null) {
      this.attributes = new HashMap<>(ref.getAttributes());
    }
    // Do not copy frozen!
  }

  /** Create a new FieldType with default properties. */
  public FieldType() {}

  /**
   * Throws an exception if this FieldType is frozen. Subclasses should call this within setters for
   * additional state.
   */
  protected void checkIfFrozen() {
    if (frozen) {
      throw new IllegalStateException("this FieldType is already frozen and cannot be changed");
    }
  }

  /**
   * Prevents future changes. Note, it is recommended that this is called once the FieldTypes's
   * properties have been set, to prevent unintentional state changes.
   */
  public void freeze() {
    this.frozen = true;
  }

  /**
   * {@inheritDoc}
   *
   * <p>The default is <code>false</code>.
   *
   * @see #setStored(boolean)
   */
  @Override
  public boolean stored() {
    return this.stored;
  }

  /**
   * Set to <code>true</code> to store this field.
   *
   * @param value true if this field should be stored.
   * @throws IllegalStateException if this FieldType is frozen against future modifications.
   * @see #stored()
   */
  public void setStored(boolean value) {
    checkIfFrozen();
    this.stored = value;
  }

  /**
   * {@inheritDoc}
   *
   * <p>The default is <code>true</code>.
   *
   * @see #setTokenized(boolean)
   */
  @Override
  public boolean tokenized() {
    return this.tokenized;
  }

  /**
   * Set to <code>true</code> to tokenize this field's contents via the configured {@link Analyzer}.
   *
   * @param value true if this field should be tokenized.
   * @throws IllegalStateException if this FieldType is frozen against future modifications.
   * @see #tokenized()
   */
  public void setTokenized(boolean value) {
    checkIfFrozen();
    this.tokenized = value;
  }

  /**
   * {@inheritDoc}
   *
   * <p>The default is <code>false</code>.
   *
   * @see #setStoreTermVectors(boolean)
   */
  @Override
  public boolean storeTermVectors() {
    return this.storeTermVectors;
  }

  /**
   * Set to <code>true</code> if this field's indexed form should be also stored into term vectors.
   *
   * @param value true if this field should store term vectors.
   * @throws IllegalStateException if this FieldType is frozen against future modifications.
   * @see #storeTermVectors()
   */
  public void setStoreTermVectors(boolean value) {
    checkIfFrozen();
    this.storeTermVectors = value;
  }

  /**
   * {@inheritDoc}
   *
   * <p>The default is <code>false</code>.
   *
   * @see #setStoreTermVectorOffsets(boolean)
   */
  @Override
  public boolean storeTermVectorOffsets() {
    return this.storeTermVectorOffsets;
  }

  /**
   * Set to <code>true</code> to also store token character offsets into the term vector for this
   * field.
   *
   * @param value true if this field should store term vector offsets.
   * @throws IllegalStateException if this FieldType is frozen against future modifications.
   * @see #storeTermVectorOffsets()
   */
  public void setStoreTermVectorOffsets(boolean value) {
    checkIfFrozen();
    this.storeTermVectorOffsets = value;
  }

  /**
   * {@inheritDoc}
   *
   * <p>The default is <code>false</code>.
   *
   * @see #setStoreTermVectorPositions(boolean)
   */
  @Override
  public boolean storeTermVectorPositions() {
    return this.storeTermVectorPositions;
  }

  /**
   * Set to <code>true</code> to also store token positions into the term vector for this field.
   *
   * @param value true if this field should store term vector positions.
   * @throws IllegalStateException if this FieldType is frozen against future modifications.
   * @see #storeTermVectorPositions()
   */
  public void setStoreTermVectorPositions(boolean value) {
    checkIfFrozen();
    this.storeTermVectorPositions = value;
  }

  /**
   * {@inheritDoc}
   *
   * <p>The default is <code>false</code>.
   *
   * @see #setStoreTermVectorPayloads(boolean)
   */
  @Override
  public boolean storeTermVectorPayloads() {
    return this.storeTermVectorPayloads;
  }

  /**
   * Set to <code>true</code> to also store token payloads into the term vector for this field.
   *
   * @param value true if this field should store term vector payloads.
   * @throws IllegalStateException if this FieldType is frozen against future modifications.
   * @see #storeTermVectorPayloads()
   */
  public void setStoreTermVectorPayloads(boolean value) {
    checkIfFrozen();
    this.storeTermVectorPayloads = value;
  }

  /**
   * {@inheritDoc}
   *
   * <p>The default is <code>false</code>.
   *
   * @see #setOmitNorms(boolean)
   */
  @Override
  public boolean omitNorms() {
    return this.omitNorms;
  }

  /**
   * Set to <code>true</code> to omit normalization values for the field.
   *
   * @param value true if this field should omit norms.
   * @throws IllegalStateException if this FieldType is frozen against future modifications.
   * @see #omitNorms()
   */
  public void setOmitNorms(boolean value) {
    checkIfFrozen();
    this.omitNorms = value;
  }

  /**
   * {@inheritDoc}
   *
   * <p>The default is {@link IndexOptions#DOCS_AND_FREQS_AND_POSITIONS}.
   *
   * @see #setIndexOptions(IndexOptions)
   */
  @Override
  public IndexOptions indexOptions() {
    return this.indexOptions;
  }

  /**
   * Sets the indexing options for the field:
   *
   * @param value indexing options
   * @throws IllegalStateException if this FieldType is frozen against future modifications.
   * @see #indexOptions()
   */
  public void setIndexOptions(IndexOptions value) {
    checkIfFrozen();
    if (value == null) {
      throw new NullPointerException("IndexOptions must not be null");
    }
    this.indexOptions = value;
  }

  /** Enables points indexing. */
  public void setDimensions(int dimensionCount, int dimensionNumBytes) {
    this.setDimensions(dimensionCount, dimensionCount, dimensionNumBytes);
  }

  /** Enables points indexing with selectable dimension indexing. */
  public void setDimensions(int dimensionCount, int indexDimensionCount, int dimensionNumBytes) {
    checkIfFrozen();
    if (dimensionCount < 0) {
      throw new IllegalArgumentException("dimensionCount must be >= 0; got " + dimensionCount);
    }
    if (dimensionCount > PointValues.MAX_DIMENSIONS) {
      throw new IllegalArgumentException(
          "dimensionCount must be <= " + PointValues.MAX_DIMENSIONS + "; got " + dimensionCount);
    }
    if (indexDimensionCount < 0) {
      throw new IllegalArgumentException(
          "indexDimensionCount must be >= 0; got " + indexDimensionCount);
    }
    if (indexDimensionCount > dimensionCount) {
      throw new IllegalArgumentException(
          "indexDimensionCount must be <= dimensionCount: "
              + dimensionCount
              + "; got "
              + indexDimensionCount);
    }
    if (indexDimensionCount > PointValues.MAX_INDEX_DIMENSIONS) {
      throw new IllegalArgumentException(
          "indexDimensionCount must be <= "
              + PointValues.MAX_INDEX_DIMENSIONS
              + "; got "
              + indexDimensionCount);
    }
    if (dimensionNumBytes < 0) {
      throw new IllegalArgumentException(
          "dimensionNumBytes must be >= 0; got " + dimensionNumBytes);
    }
    if (dimensionNumBytes > PointValues.MAX_NUM_BYTES) {
      throw new IllegalArgumentException(
          "dimensionNumBytes must be <= "
              + PointValues.MAX_NUM_BYTES
              + "; got "
              + dimensionNumBytes);
    }
    if (dimensionCount == 0) {
      if (indexDimensionCount != 0) {
        throw new IllegalArgumentException(
            "when dimensionCount is 0, indexDimensionCount must be 0; got " + indexDimensionCount);
      }
      if (dimensionNumBytes != 0) {
        throw new IllegalArgumentException(
            "when dimensionCount is 0, dimensionNumBytes must be 0; got " + dimensionNumBytes);
      }
    } else if (indexDimensionCount == 0) {
      throw new IllegalArgumentException(
          "when dimensionCount is > 0, indexDimensionCount must be > 0; got "
              + indexDimensionCount);
    } else if (dimensionNumBytes == 0) {
      if (dimensionCount != 0) {
        throw new IllegalArgumentException(
            "when dimensionNumBytes is 0, dimensionCount must be 0; got " + dimensionCount);
      }
    }

    this.dimensionCount = dimensionCount;
    this.indexDimensionCount = indexDimensionCount;
    this.dimensionNumBytes = dimensionNumBytes;
  }

  @Override
  public int pointDimensionCount() {
    return dimensionCount;
  }

  @Override
  public int pointIndexDimensionCount() {
    return indexDimensionCount;
  }

  @Override
  public int pointNumBytes() {
    return dimensionNumBytes;
  }

  /** Enable vector indexing, with the specified number of dimensions and distance function. */
  public void setVectorDimensionsAndSimilarityFunction(
      int numDimensions, VectorValues.SimilarityFunction distFunc) {
    checkIfFrozen();
    if (numDimensions <= 0) {
      throw new IllegalArgumentException("vector numDimensions must be > 0; got " + numDimensions);
    }
    if (numDimensions > VectorValues.MAX_DIMENSIONS) {
      throw new IllegalArgumentException(
          "vector numDimensions must be <= VectorValues.MAX_DIMENSIONS (="
              + VectorValues.MAX_DIMENSIONS
              + "); got "
              + numDimensions);
    }
    this.vectorDimension = numDimensions;
    this.vectorSimilarityFunction = distFunc;
  }

  @Override
  public int vectorDimension() {
    return vectorDimension;
  }

  @Override
  public VectorValues.SimilarityFunction vectorSimilarityFunction() {
    return vectorSimilarityFunction;
  }

  /**
   * Puts an attribute value.
   *
   * <p>This is a key-value mapping for the field that the codec can use to store additional
   * metadata.
   *
   * <p>If a value already exists for the field, it will be replaced with the new value. This method
   * is not thread-safe, user must not add attributes while other threads are indexing documents
   * with this field type.
   *
   * @lucene.experimental
   */
  public String putAttribute(String key, String value) {
    checkIfFrozen();
    if (attributes == null) {
      attributes = new HashMap<>();
    }
    return attributes.put(key, value);
  }

  @Override
  public Map<String, String> getAttributes() {
    return attributes;
  }

  /** Prints a Field for human consumption. */
  @Override
  public String toString() {
    StringBuilder result = new StringBuilder();
    if (stored()) {
      result.append("stored");
    }
    if (indexOptions != IndexOptions.NONE) {
      if (result.length() > 0) result.append(",");
      result.append("indexed");
      if (tokenized()) {
        result.append(",tokenized");
      }
      if (storeTermVectors()) {
        result.append(",termVector");
      }
      if (storeTermVectorOffsets()) {
        result.append(",termVectorOffsets");
      }
      if (storeTermVectorPositions()) {
        result.append(",termVectorPosition");
      }
      if (storeTermVectorPayloads()) {
        result.append(",termVectorPayloads");
      }
      if (omitNorms()) {
        result.append(",omitNorms");
      }
      if (indexOptions != IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) {
        result.append(",indexOptions=");
        result.append(indexOptions);
      }
    }
    if (dimensionCount != 0) {
      if (result.length() > 0) {
        result.append(",");
      }
      result.append("pointDimensionCount=");
      result.append(dimensionCount);
      result.append(",pointIndexDimensionCount=");
      result.append(indexDimensionCount);
      result.append(",pointNumBytes=");
      result.append(dimensionNumBytes);
    }
    if (docValuesType != DocValuesType.NONE) {
      if (result.length() > 0) {
        result.append(",");
      }
      result.append("docValuesType=");
      result.append(docValuesType);
    }

    return result.toString();
  }

  /**
   * {@inheritDoc}
   *
   * <p>The default is <code>null</code> (no docValues)
   *
   * @see #setDocValuesType(DocValuesType)
   */
  @Override
  public DocValuesType docValuesType() {
    return docValuesType;
  }

  /**
   * Sets the field's DocValuesType
   *
   * @param type DocValues type, or null if no DocValues should be stored.
   * @throws IllegalStateException if this FieldType is frozen against future modifications.
   * @see #docValuesType()
   */
  public void setDocValuesType(DocValuesType type) {
    checkIfFrozen();
    if (type == null) {
      throw new NullPointerException("DocValuesType must not be null");
    }
    docValuesType = type;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + dimensionCount;
    result = prime * result + indexDimensionCount;
    result = prime * result + dimensionNumBytes;
    result = prime * result + ((docValuesType == null) ? 0 : docValuesType.hashCode());
    result = prime * result + indexOptions.hashCode();
    result = prime * result + (omitNorms ? 1231 : 1237);
    result = prime * result + (storeTermVectorOffsets ? 1231 : 1237);
    result = prime * result + (storeTermVectorPayloads ? 1231 : 1237);
    result = prime * result + (storeTermVectorPositions ? 1231 : 1237);
    result = prime * result + (storeTermVectors ? 1231 : 1237);
    result = prime * result + (stored ? 1231 : 1237);
    result = prime * result + (tokenized ? 1231 : 1237);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    FieldType other = (FieldType) obj;
    if (dimensionCount != other.dimensionCount) return false;
    if (indexDimensionCount != other.indexDimensionCount) return false;
    if (dimensionNumBytes != other.dimensionNumBytes) return false;
    if (docValuesType != other.docValuesType) return false;
    if (indexOptions != other.indexOptions) return false;
    if (omitNorms != other.omitNorms) return false;
    if (storeTermVectorOffsets != other.storeTermVectorOffsets) return false;
    if (storeTermVectorPayloads != other.storeTermVectorPayloads) return false;
    if (storeTermVectorPositions != other.storeTermVectorPositions) return false;
    if (storeTermVectors != other.storeTermVectors) return false;
    if (stored != other.stored) return false;
    if (tokenized != other.tokenized) return false;
    return true;
  }
}
