package org.jetbrains.jps.incremental.storage;

import com.intellij.util.ArrayUtil;
import com.intellij.util.io.DataExternalizer;
import org.jetbrains.jps.Relativator;
import org.jetbrains.jps.builders.BuildTarget;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;

/**
 * @author Sergey Serebryakov
 */

/**
 * AbstractStateStorage is parametrized by type of key and type of the state it stores.
 * In this case, the state is an array of ChecksumPerTarget records.
 */
public class ChecksumStorage extends AbstractStateStorage<File, ChecksumStorage.ChecksumPerTarget[]> implements Checksums {
  /**
   * Is needed to get the ID of a build target by the target itself.
   */
  private final BuildTargetsState myTargetsState;

  public ChecksumStorage(File storePath, BuildTargetsState targetsState, Relativator relativator) throws IOException {
    super(storePath, new RelativeFileKeyDescriptor(relativator), new ChecksumStateExternalizer());
    myTargetsState = targetsState;
  }

  private static ChecksumPerTarget[] updateState(ChecksumPerTarget[] oldState, final int targetId, String checksum) {
    final ChecksumPerTarget newItem = new ChecksumPerTarget(targetId, checksum);
    if (oldState == null) {
      return new ChecksumPerTarget[]{newItem};
    }
    for (int i = 0; i < oldState.length; i++) {
      if (oldState[i].targetId == targetId) {
        oldState[i] = newItem;
        return oldState;
      }
    }
    return ArrayUtil.append(oldState, newItem);
  }

  @Override
  public void saveChecksum(File file, BuildTarget<?> buildTarget, String checksum) throws IOException {
    int targetId = myTargetsState.getBuildTargetId(buildTarget);
    ChecksumPerTarget[] oldState = getState(file);
    ChecksumPerTarget[] newState = updateState(oldState, targetId, checksum);
    update(file, newState);
  }

  @Override
  public String getChecksum(File file, BuildTarget<?> target) throws IOException {
    final ChecksumPerTarget[] state = getState(file);
    if (state != null) {
      int targetId = myTargetsState.getBuildTargetId(target);
      for (ChecksumPerTarget record : state) {
        if (record.targetId == targetId) {
          return record.checksum;
        }
      }
    }
    return "NO SUCH FILE IN THE CURRENT STATE";
  }

  @Override
  public void removeChecksum(File file, BuildTarget<?> buildTarget) throws IOException {
    ChecksumPerTarget[] oldState = getState(file);
    if (oldState != null) {
      int targetId = myTargetsState.getBuildTargetId(buildTarget);
      for (int i = 0; i < oldState.length; i++) {
        ChecksumPerTarget record = oldState[i];
        if (record.targetId == targetId) {
          if (oldState.length == 1) {
            remove(file);
          }
          else {
            ChecksumPerTarget[] newState = ArrayUtil.remove(oldState, i);
            update(file, newState);
            break;
          }
        }
      }
    }
  }

  @Override
  public void clean() throws IOException {
    super.clean();
  }

  @Override
  public void force() {
    super.force();
  }

  /**
   * An elementary record containing a pair (target ID, checksum).
   */
  public static class ChecksumPerTarget {
    public final int targetId;
    public final String checksum;

    public ChecksumPerTarget(int targetId, String checksum) {
      this.targetId = targetId;
      this.checksum = checksum;
    }
  }

  /**
   * The externalizer is parametrized by the type of the state this storage stores.
   * In this case, the state is an array of ChecksumPerTarget records.
   */
  private static class ChecksumStateExternalizer implements DataExternalizer<ChecksumPerTarget[]> {

    @Override
    public void save(DataOutput out, ChecksumPerTarget[] value) throws IOException {
      out.writeInt(value.length);
      for (ChecksumPerTarget record : value) {
        out.writeInt(record.targetId);
        out.writeInt(record.checksum.length());
        out.writeChars(record.checksum);
      }
    }

    @Override
    public ChecksumPerTarget[] read(DataInput in) throws IOException {
      int size = in.readInt();
      ChecksumPerTarget[] records = new ChecksumPerTarget[size];
      for (int i = 0; i < size; i++) {
        int targetId = in.readInt();
        int length = in.readInt();
        char[] chars = new char[length];
        for (int j = 0; j < length; j++) {
          chars[j] = in.readChar();
        }
        String checksum = new String(chars);
        records[i] = new ChecksumPerTarget(targetId, checksum);
      }
      return records;
    }
  }
}
