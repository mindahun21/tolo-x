import React, { useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

const OAuth2Callback = () => {
    const location = useLocation();
    const navigate = useNavigate();
    const queryParams = new URLSearchParams(location.search);
    const error = queryParams.get('error');

    useEffect(() => {
        if (!error) {
            navigate('/');
        }
    }, [error, navigate]);

    return (
        <div>
            {error ? (
                <div>
                    <h1>Authentication Error</h1>
                    <p>{error}</p>
                </div>
            ) : (
                <div>
                    <h1>Authentication Successful</h1>
                    <p>You are being redirected...</p>
                </div>
            )}
        </div>
    );
};

export default OAuth2Callback;
