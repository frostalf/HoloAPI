package com.captainbern.hug;

import com.google.common.base.Preconditions;

import java.io.*;

import static com.captainbern.hug.Constant.*;

public class ClassHug {

    private int magic;

    private int minor;

    private int major;

    private int poolSize;

    private Constant[] pool;

    private int accessFlags;

    private int thisClass;

    private int superClass;

    private byte[] otherCode;

    private byte[] code;

    public ClassHug(byte[] b) {
        this(b, 0, b.length);
    }

    public ClassHug(byte[] b, int offset, int length) {
        Preconditions.checkNotNull(b);
        Preconditions.checkPositionIndex(offset, b.length);
        Preconditions.checkState(length >= b.length);

        byte[] code = new byte[length];
        System.arraycopy(b, 0, code, 0, b.length);

        DataInputStream inputStream = new DataInputStream(new BufferedInputStream(new ByteArrayInputStream(code), 8192));

        try {

            this.code = code;

            this.magic = inputStream.readInt();
            this.minor = inputStream.readUnsignedShort();
            this.major = inputStream.readUnsignedShort();

            if (major > 0x34)
                throw new RuntimeException("Unsupported class file!");

            this.poolSize = inputStream.readUnsignedShort();
            this.pool = new Constant[this.poolSize];

            for (int i = 1; i < poolSize; i++) {
                // The type
                byte type = inputStream.readByte();
                // Used to read values with a bigger size blah
                byte[] data;
                switch (type) {
                    case CONSTANT_Utf8:
                        pool[i] = new Constant(type, i, inputStream.readUTF().getBytes());
                        break;
                    case CONSTANT_Class:
                    case CONSTANT_String:
                    case CONSTANT_MethodType:
                        data = new byte[2];
                        inputStream.readFully(data);
                        pool[i] = new Constant(type, i, data);
                        break;
                    case CONSTANT_Integer:
                    case CONSTANT_Float:
                    case CONSTANT_Fieldref:
                    case CONSTANT_Methodref:
                    case CONSTANT_InterfaceMethodref:
                    case CONSTANT_NameAndType:
                    case CONSTANT_InvokeDynamic:
                        data = new byte[4];
                        inputStream.readFully(data);
                        pool[i] = new Constant(type, i, data);
                        break;
                    case CONSTANT_Long:
                    case CONSTANT_Double:
                        data = new byte[8];
                        inputStream.readFully(data);
                        pool[i] = new Constant(type, i, data);
                        i++;
                        break;
                    case CONSTANT_MethodHandle:
                        data = new byte[3];
                        inputStream.readFully(data);
                        pool[i] = new Constant(type, i, data);
                        break;
                    default:
                        throw new RuntimeException("Illegal constant-type: " + type + "!");
                }
            }

            this.accessFlags = inputStream.readUnsignedShort();
            this.thisClass = inputStream.readUnsignedShort();
            this.superClass = inputStream.readUnsignedShort();

            // Write the other parts of the class to a byte-array
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            DataOutputStream outputStream = new DataOutputStream(new BufferedOutputStream(byteStream, 8192));
            byte[] buffer = new byte[inputStream.available()];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();
            outputStream.close();
            this.otherCode = byteStream.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Failed to hug class: " + b + "!");
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Constructs a new ClassHug with the given source.
     * @param classSource
     */
    public ClassHug(String classSource) {
        this(toBytes(ClassLoader.getSystemResourceAsStream(classSource.replace('.', '/') + ".class"), true));
    }

    /**
     * Converst a given InputStream to a byte-array.
     * @param inputStream
     * @param close
     * @return
     */
    private static byte[] toBytes(final InputStream inputStream, boolean close) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            byte[] buffer = new byte[inputStream.available()];

            int n;
            while (-1 != (n = inputStream.read(buffer))) {
                outputStream.write(buffer, 0, n);
            }

            return buffer;

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (close) {
                    inputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * Returns the raw-bytecode representation of this class
     * @return
     */
    public byte[] getHugCode() {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(8192);
        DataOutputStream dataOutputStream = new DataOutputStream(new BufferedOutputStream(byteArrayOutputStream, 8192));

        try {
            write(dataOutputStream);
        } catch (IOException ioe) {
            ioe.printStackTrace();
            try {
                dataOutputStream.close();
            } catch (IOException ioe1) {
                ioe1.printStackTrace();
            }
        }

        return byteArrayOutputStream.toByteArray();
    }

    /**
     * Writes this class to the given OutputStream
     */
    public final void write(DataOutputStream codeStream) throws IOException {
        codeStream.writeInt(this.magic);

        codeStream.writeShort(this.minor);
        codeStream.writeShort(this.major);

        codeStream.writeShort(this.poolSize);
        for (int i = 1; i < this.poolSize; i++) {
            Constant constant = this.pool[i];
            if (constant != null) {
                codeStream.write(constant.getBytes());
            }
        }

        codeStream.writeShort(this.accessFlags);
        codeStream.writeShort(this.thisClass);
        codeStream.writeShort(this.superClass);

        codeStream.write(this.otherCode);

        codeStream.flush();
    }

    public byte[] getUneditedCode() {
        return this.code;
    }

    public int getAccessFlags() {
        return this.accessFlags;
    }

    public void setAccessFlags(int accessFlags) {
        this.accessFlags = accessFlags;
    }

    public String getClassName() {
        Constant constant = this.pool[this.thisClass];

        if (constant.getType() == CONSTANT_Class) {
            byte[] val = constant.getRawData();
            int index = ((val[0] << 8) | (val[1]));

            Constant utf8 = this.pool[index];
            if (utf8.getType() == CONSTANT_Utf8)
                return utf8.rawStringValue();
        }

        throw new RuntimeException("Failed to return the Class-name! Perhaps a corrupted ConstantPool?");
    }

    public void setClassName(String className) {
        Constant constant = this.pool[this.thisClass];

        if (constant.getType() == CONSTANT_Class) {
            byte[] val = constant.getRawData();
            int index = ((val[0] << 8) | val[1]);

            Constant utf8 = this.pool[index];
            if (utf8.getType() == CONSTANT_Utf8) {
                utf8.setRawData(className.getBytes());
                return;
            }
        }

        throw new RuntimeException("Failed to set the Class-name! Perhaps a corrupted ConstantPool?");
    }

    public String getSuperClassName() {
        Constant constant = this.pool[this.superClass];

        if (constant.getType() == CONSTANT_Class) {
            byte[] val = constant.getRawData();
            int index = ((val[0] << 8) | (val[1]));

            Constant utf8 = this.pool[index];
            if (utf8.getType() == CONSTANT_Utf8)
                return utf8.rawStringValue();
        }

        throw new RuntimeException("Failed to return the SuperClass-name! Perhaps a corrupted ConstantPool?");
    }

    public void setSuperClassName(String className) {
        Constant constant = this.pool[this.superClass];

        if (constant.getType() == CONSTANT_Class) {
            byte[] val = constant.getRawData();
            int index = ((val[0] << 8) | val[1]);

            Constant utf8 = this.pool[index];
            if (utf8.getType() == CONSTANT_Utf8) {
                utf8.setRawData(className.getBytes());
                return;
            }
        }

        throw new RuntimeException("Failed to set the SuperClass-name! Perhaps a corrupted ConstantPool?");
    }

    /**
     * Replaces all instances of the given string to {@param to} in the ConstantPool
     * @param from
     * @param to
     */
    public void replace(String from, String to) {
        for (int i = 1; i < this.poolSize; i++) {
            Constant constant = this.pool[i];
            if (constant != null) {
                if (constant.getType() == CONSTANT_Utf8) {
                    constant.setRawData(constant.rawStringValue().replaceAll(from, to).getBytes());
                }
            }
        }
    }

    public Class giveAHug() {
        return new ClassLoader() {
            public Class defineClass(String name, byte[] code) {
                return super.defineClass(name, code, 0, code.length);
            }
        }.defineClass(this.getClassName().replace('/', '.'), this.getHugCode());
    }
}
