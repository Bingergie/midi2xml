<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema">

    <!-- Identity template to copy all nodes by default -->
    <xsl:template match="@*|node()">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>

    <!-- Rename the second <chord> element in the <note> complexType to <chord2> -->
    <xsl:template match="//xs:complexType[@name='note']//xs:element[@name='chord'][2]">
        <xs:element name="chord2" type="{@type}" minOccurs="{@minOccurs}" maxOccurs="{@maxOccurs}">
            <xsl:apply-templates select="node()"/>
        </xs:element>
    </xsl:template>

    <!-- Flatten nested <xs:choice> structures in <note> to simplify XJC mapping -->
    <xsl:template match="//xs:complexType[@name='note']//xs:choice">
        <xsl:apply-templates/>
    </xsl:template>

</xsl:stylesheet>