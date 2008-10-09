/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.pdfbox.cos;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

import org.apache.pdfbox.persistence.util.COSHEXTable;

import org.apache.pdfbox.exceptions.COSVisitorException;

/**
 * This represents a string object in a PDF document.
 *
 * @author <a href="mailto:ben@benlitchfield.com">Ben Litchfield</a>
 * @version $Revision: 1.30 $
 */
public class COSString extends COSBase
{
    /**
     * One of the open string tokens.
     */
    public static final byte[] STRING_OPEN = new byte[]{ 40 }; //"(".getBytes();
    /**
     * One of the close string tokens.
     */
    public static final byte[] STRING_CLOSE = new byte[]{ 41 }; //")".getBytes( "ISO-8859-1" );
    /**
     * One of the open string tokens.
     */
    public static final byte[] HEX_STRING_OPEN = new byte[]{ 60 }; //"<".getBytes( "ISO-8859-1" );
    /**
     * One of the close string tokens.
     */
    public static final byte[] HEX_STRING_CLOSE = new byte[]{ 62 }; //">".getBytes( "ISO-8859-1" );
    /**
     * the escape character in strings.
     */
    public static final byte[] ESCAPE = new byte[]{ 92 }; //"\\".getBytes( "ISO-8859-1" );

    /**
     * CR escape characters.
     */
    public static final byte[] CR_ESCAPE = new byte[]{ 92, 114 }; //"\\r".getBytes( "ISO-8859-1" );
    /**
     * LF escape characters.
     */
    public static final byte[] LF_ESCAPE = new byte[]{ 92, 110 }; //"\\n".getBytes( "ISO-8859-1" );
    /**
     * HT escape characters.
     */
    public static final byte[] HT_ESCAPE = new byte[]{ 92, 116 }; //"\\t".getBytes( "ISO-8859-1" );
    /**
     * BS escape characters.
     */
    public static final byte[] BS_ESCAPE = new byte[]{ 92, 98 }; //"\\b".getBytes( "ISO-8859-1" );
    /**
     * FF escape characters.
     */
    public static final byte[] FF_ESCAPE = new byte[]{ 92, 102 }; //"\\f".getBytes( "ISO-8859-1" );

    private ByteArrayOutputStream out = null;

    /**
     * Forces the string to be serialized in literal form but not hexa form.
     */
    private boolean forceLiteralForm = false;


    /**
     * Constructor.
     */
    public COSString()
    {
        out = new ByteArrayOutputStream();
    }

    /**
     * Explicit constructor for ease of manual PDF construction.
     *
     * @param value The string value of the object.
     */
    public COSString( String value )
    {
        try
        {
            boolean unicode16 = false;
            char[] chars = value.toCharArray();
            for( int i=0; i<chars.length; i++ )
            {
                if( chars[i] > 255 )
                {
                    unicode16 = true;
                }
            }
            if( unicode16 )
            {
                byte[] data = value.getBytes( "UTF-16BE" );
                out = new ByteArrayOutputStream( data.length +2);
                out.write( 0xFE );
                out.write( 0xFF );
                out.write( data );
            }
            else
            {
                byte[] data = value.getBytes("ISO-8859-1");
                out = new ByteArrayOutputStream( data.length );
                out.write( data );
            }
        }
        catch (IOException ignore)
        {
            ignore.printStackTrace();
            //should never happen
        }
    }

    /**
     * Explicit constructor for ease of manual PDF construction.
     *
     * @param value The string value of the object.
     */
    public COSString( byte[] value )
    {
        try
        {
            out = new ByteArrayOutputStream( value.length );
            out.write( value );
        }
        catch (IOException ignore)
        {
            ignore.printStackTrace();
            //should never happen
        }
    }

    /**
     * Forces the string to be written in literal form instead of hexadecimal form.
     *
     * @param v if v is true the string will be written in literal form, otherwise it will
     * be written in hexa if necessary.
     */

    public void setForceLiteralForm(boolean v)
    {
        forceLiteralForm = v;
    }

    /**
     * This will create a COS string from a string of hex characters.
     *
     * @param hex A hex string.
     * @return A cos string with the hex characters converted to their actual bytes.
     * @throws IOException If there is an error with the hex string.
     */
    public static COSString createFromHexString( String hex ) throws IOException
    {
        COSString retval = new COSString();
        StringBuffer hexBuffer = new StringBuffer( hex.trim() );
        //if odd number then the last hex digit is assumed to be 0
        if( hexBuffer.length() % 2 == 1 )
        {
            hexBuffer.append( "0" );
        }
        for( int i=0; i<hexBuffer.length();)
        {
            String hexChars = "" + hexBuffer.charAt( i++ ) + hexBuffer.charAt( i++ );
            try
            {
                retval.append( Integer.parseInt( hexChars, 16 ) );
            }
            catch( NumberFormatException e )
            {
                throw new IOException( "Error: Expected hex number, actual='" + hexChars + "'" );
            }
        }
        return retval;
    }

    /**
     * This will take this string and create a hex representation of the bytes that make the string.
     *
     * @return A hex string representing the bytes in this string.
     */
    public String getHexString()
    {
        StringBuffer retval = new StringBuffer( out.size() * 2 );
        byte[] data = getBytes();
        for( int i=0; i<data.length; i++ )
        {
            retval.append( COSHEXTable.HEX_TABLE[ (data[i]+256)%256 ] );
        }

        return retval.toString();
    }

    /**
     * This will get the string that this object wraps.
     *
     * @return The wrapped string.
     */
    public String getString()
    {
        String retval;
        String encoding = "ISO-8859-1";
        byte[] data = getBytes();
        int start = 0;
        if( data.length > 2 )
        {
            if( data[0] == (byte)0xFF && data[1] == (byte)0xFE )
            {
                encoding = "UTF-16LE";
                start=2;
            }
            else if( data[0] == (byte)0xFE && data[1] == (byte)0xFF )
            {
                encoding = "UTF-16BE";
                start=2;
            }
        }
        try
        {
            retval = new String( getBytes(), start, data.length-start, encoding );
        }
        catch( UnsupportedEncodingException e )
        {
            //should never happen
            e.printStackTrace();
            retval = new String( getBytes() );
        }
        return retval;
    }

    /**
     * This will append a byte[] to the string.
     *
     * @param data The byte[] to add to this string.
     *
     * @throws IOException If an IO error occurs while writing the byte.
     */
    public void append( byte[] data ) throws IOException
    {
        out.write( data );
    }

    /**
     * This will append a byte to the string.
     *
     * @param in The byte to add to this string.
     *
     * @throws IOException If an IO error occurs while writing the byte.
     */
    public void append( int in ) throws IOException
    {
        out.write( in );
    }

    /**
     * This will reset the internal buffer.
     */
    public void reset()
    {
        out.reset();
    }

    /**
     * This will get the bytes of the string.
     *
     * @return A byte array that represents the string.
     */
    public byte[] getBytes()
    {
        return out.toByteArray();
    }

    /**
     * {@inheritDoc}
     */
    public String toString()
    {
        return "COSString{" + new String( getBytes() ) + "}";
    }

    /**
     * This will output this string as a PDF object.
     *
     * @param output The stream to write to.
     * @throws IOException If there is an error writing to the stream.
     */
    public void writePDF( OutputStream output ) throws IOException
    {
        boolean outsideASCII = false;
        //Lets first check if we need to escape this string.
        byte[] bytes = getBytes();
        for( int i=0; i<bytes.length && !outsideASCII; i++ )
        {
            //if the byte is negative then it is an eight bit byte and is
            //outside the ASCII range.
            outsideASCII = bytes[i] <0;
        }
        if( !outsideASCII || forceLiteralForm )
        {
            output.write(STRING_OPEN);
            for( int i=0; i<bytes.length; i++ )
            {
                int b = (bytes[i]+256)%256;
                switch( b )
                {
                    case '(':
                    case ')':
                    case '\\':
                    {
                        output.write(ESCAPE);
                        output.write(b);
                        break;
                    }
                    case 10: //LF
                    {
                        output.write( LF_ESCAPE );
                        break;
                    }
                    case 13: // CR
                    {
                        output.write( CR_ESCAPE );
                        break;
                    }
                    case '\t':
                    {
                        output.write( HT_ESCAPE );
                        break;
                    }
                    case '\b':
                    {
                        output.write( BS_ESCAPE );
                        break;
                    }
                    case '\f':
                    {
                        output.write( FF_ESCAPE );
                        break;
                    }
                    default:
                    {
                        output.write( b );
                    }
                }
            }
            output.write(STRING_CLOSE);
        }
        else
        {
            output.write(HEX_STRING_OPEN);
            for(int i=0; i<bytes.length; i++ )
            {
                output.write( COSHEXTable.TABLE[ (bytes[i]+256)%256 ] );
            }
            output.write(HEX_STRING_CLOSE);
        }
    }



    /**
     * visitor pattern double dispatch method.
     *
     * @param visitor The object to notify when visiting this object.
     * @return any object, depending on the visitor implementation, or null
     * @throws COSVisitorException If an error occurs while visiting this object.
     */
    public Object accept(ICOSVisitor visitor) throws COSVisitorException
    {
        return visitor.visitFromString( this );
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(Object obj)
    {
        return (obj instanceof COSString) && java.util.Arrays.equals(((COSString) obj).getBytes(), getBytes());
    }

    /**
     * {@inheritDoc}
     */
    public int hashCode()
    {
        return getBytes().hashCode();
    }
}