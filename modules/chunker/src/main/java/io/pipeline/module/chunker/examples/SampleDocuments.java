package io.pipeline.module.chunker.examples;

public final class SampleDocuments {
    
    private SampleDocuments() {
        // Utility class
    }
    
    public static final String US_CONSTITUTION_PREAMBLE = """
        We the People of the United States, in Order to form a more perfect Union, establish Justice, insure domestic Tranquility, provide for the common defence, promote the general Welfare, and secure the Blessings of Liberty to ourselves and our Posterity, do ordain and establish this Constitution for the United States of America.
        
        Article I
        
        Section 1. All legislative Powers herein granted shall be vested in a Congress of the United States, which shall consist of a Senate and House of Representatives.
        
        Section 2. The House of Representatives shall be composed of Members chosen every second Year by the People of the several States, and the Electors in each State shall have the Qualifications requisite for Electors of the most numerous Branch of the State Legislature.
        
        No Person shall be a Representative who shall not have attained to the Age of twenty five Years, and been seven Years a Citizen of the United States, and who shall not, when elected, be an Inhabitant of that State in which he shall be chosen.
        """;
    
    public static final String TECHNICAL_DOCUMENTATION = """
        API Documentation: User Authentication
        
        Overview
        The User Authentication API provides secure access control for applications using JWT tokens and OAuth 2.0 flows.
        
        Authentication Flow
        1. Client requests authorization from the authorization server
        2. Authorization server authenticates the user and issues an authorization code
        3. Client exchanges the authorization code for an access token
        4. Client uses the access token to access protected resources
        
        Endpoints
        POST /auth/login - Authenticate user credentials
        POST /auth/refresh - Refresh expired access token
        POST /auth/logout - Invalidate user session
        GET /auth/profile - Retrieve authenticated user profile
        
        Request Headers
        Authorization: Bearer <access_token>
        Content-Type: application/json
        
        Error Handling
        All API endpoints return standardized error responses with appropriate HTTP status codes and descriptive error messages.
        """;
    
    public static final String LITERARY_EXCERPT = """
        Chapter 1: The Beginning
        
        It was the best of times, it was the worst of times, it was the age of wisdom, it was the age of foolishness, it was the epoch of belief, it was the epoch of incredulity, it was the season of Light, it was the season of Darkness, it was the spring of hope, it was the winter of despair.
        
        We had everything before us, we had nothing before us, we were all going direct to Heaven, we were all going direct the other way – in short, the period was so far like the present period, that some of its noisiest authorities insisted on its being received, for good or for evil, in the superlative degree of comparison only.
        
        There were a king with a large jaw and a queen with a plain face, on the throne of England; there were a king with a large jaw and a queen with a fair face, on the throne of France. In both countries it was clearer than crystal to the lords of the State preserves of loaves and fishes, that things in general were settled for ever.
        """;
    
    public static final String NEWS_ARTICLE = """
        Breaking: Major Technology Breakthrough Announced
        
        SAN FRANCISCO, CA - A leading technology company announced today a significant breakthrough in quantum computing that could revolutionize data processing capabilities across industries.
        
        The new quantum processor, developed over five years of research, demonstrates unprecedented stability and error correction rates. Initial tests show performance improvements of up to 1000x over traditional computing methods for specific algorithmic tasks.
        
        "This represents a fundamental shift in how we approach complex computational problems," said Dr. Sarah Chen, lead researcher on the project. "Applications range from drug discovery and financial modeling to climate simulation and artificial intelligence."
        
        The technology is expected to enter limited commercial availability within the next two years, with broader deployment anticipated by 2027. Industry experts predict this advancement could accelerate scientific research and enable previously impossible computational tasks.
        
        Stock markets responded positively to the announcement, with technology sector indices rising 3.2% in early trading.
        """;
    
    public static final String[] SAMPLE_TEXTS = {
        US_CONSTITUTION_PREAMBLE,
        TECHNICAL_DOCUMENTATION,
        LITERARY_EXCERPT,
        NEWS_ARTICLE
    };
}