/*
 * This software is licensed under the Apache 2 license, quoted below.
 *
 * Copyright 2017 Astraea, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *     [http://www.apache.org/licenses/LICENSE-2.0]
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */

package astraea.spark.rasterframes

import geotrellis.util.MethodExtensions
import org.apache.spark.sql.jts.SQLTypes
import org.apache.spark.sql.{SQLContext, gt}

/**
 * Extension methods on [[SQLContext]] for initializing RasterFrames support in Catalyst.
 *
 * @author sfitch 
 * @since 10/30/17
 */
trait SQLContextMethods extends MethodExtensions[SQLContext] {
  def withRasterFrames: SQLContext = {
    SQLTypes.init(self) // <-- JTS types.
    gt.register(self)
    functions.register(self)
    expressions.register(self)
    self
  }
}
