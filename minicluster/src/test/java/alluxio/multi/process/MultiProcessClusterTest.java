/*
 * The Alluxio Open Foundation licenses this work under the Apache License, version 2.0
 * (the "License"). You may not use this work except in compliance with the License, which is
 * available at www.apache.org/licenses/LICENSE-2.0
 *
 * This software is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied, as more fully set forth in the License.
 *
 * See the NOTICE file distributed with this work for information regarding copyright ownership.
 */

package alluxio.multi.process;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import alluxio.AlluxioURI;
import alluxio.Constants;
import alluxio.PropertyKey;
import alluxio.client.file.FileInStream;
import alluxio.client.file.FileSystem;
import alluxio.client.file.FileSystemTestUtils;
import alluxio.client.file.options.CreateFileOptions;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

public final class MultiProcessClusterTest {
  private boolean mSuccess = false;
  private MultiProcessCluster mCluster;

  @Rule
  public Timeout mTimeout = Timeout.millis(Constants.MAX_TEST_DURATION_MS);

  @Test
  public void simpleCluster() throws Exception {
    mCluster = MultiProcessCluster.newBuilder()
        .setClusterName("simpleCluster")
        .setNumMasters(1)
        .setNumWorkers(1)
        .build();
    try {
      mCluster.start();
      FileSystem fs = mCluster.getFileSystemClient();
      createAndOpenFile(fs);
      mSuccess = true;
    } finally {
      cleanup();
    }
  }

  @Test
  public void zookeeper() throws Exception {
    mCluster = MultiProcessCluster.newBuilder()
        .setClusterName("zookeeper")
        .addProperty(PropertyKey.ZOOKEEPER_ENABLED, "true")
        .setNumMasters(3)
        .setNumWorkers(2)
        .build();
    try {
      mCluster.start();
      FileSystem fs = mCluster.getFileSystemClient();
      createAndOpenFile(fs);
      mSuccess = true;
    } finally {
      cleanup();
    }
  }

  /**
   * Destroys the test cluster, saving its work directory in case of failure.
   *
   * This cannot be done with @After because @After methods are not necessarily called when a test
   * times out due to a Timeout rule.
   */
  private void cleanup() throws Exception {
    if (mCluster != null) {
      if (!mSuccess) {
        mCluster.saveWorkdir();
      }
      mCluster.destroy();
    }
  }

  private static void createAndOpenFile(FileSystem fs) throws Exception {
    long timeoutMs = System.currentTimeMillis() + Constants.MINUTE_MS;
    int len = 10;
    for (;;) {
      String testFile = "/fileName";
      try {
        FileSystemTestUtils.createByteFile(fs, testFile, len,
            CreateFileOptions.defaults().setBlockSizeBytes(100));
        break;
      } catch (Exception e) {
        // This can indicate that the worker hasn't connected yet, so we must delete and retry.
        if (System.currentTimeMillis() < timeoutMs) {
          if (fs.exists(new AlluxioURI(testFile))) {
            fs.delete(new AlluxioURI(testFile));
          }
        } else {
          fail(String.format("Timed out trying to create a file. Latest exception: %s",
              e.toString()));
        }
      }
    }
    try (FileInStream is = fs.openFile(new AlluxioURI("/fileName"))) {
      assertEquals(10, is.remaining());
    }
  }
}
