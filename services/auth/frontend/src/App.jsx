// App.jsx
import React, { useEffect, useState } from "react";
import { Routes, Route } from "react-router-dom";
import { httpClient } from "./HttpClient.js";
import OAuth2Callback from "./OAuth2Callback.jsx";

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/oauth2/callback" element={<OAuth2Callback />} />
    </Routes>
  );
}

const Home = () => {
  const [user, setUser] = useState(null);
  const [checkResult, setCheckResult] = useState("");

  const loginWithGoogle = () => {
    window.location.href = "http://localhost:8222/oauth2/authorize/google?redirect_uri=http://localhost:5173/oauth2/callback&client_type=web";
  };

  useEffect(() => {
    fetchUser();
  }, []);

  const fetchUser = async () => {
    try {
      const response = await httpClient.get("/me");
      setUser(response.data);
    } catch (err) {
      console.log("Not logged in");
    }
  };

  const callCheck = async () => {
    try {
      const response = await httpClient.get("/check");
      setCheckResult(JSON.stringify(response.data));
    } catch (err) {
      setCheckResult("Unauthorized / Not Logged In");
    }
  };

  return (
    <div style={{ padding: "30px", fontFamily: "Arial" }}>
      <h1>OAuth2 Login Demo</h1>

      {!user ? (
        <>
          <button onClick={loginWithGoogle} style={btnStyle}>
            Login with Google
          </button>
        </>
      ) : (
        <>
          <h3>Welcome, {user.name || user.email}</h3>
          <button onClick={callCheck} style={btnStyle}>
            Call /check Endpoint
          </button>

          {checkResult && (
            <pre style={{ background: "#eee", padding: "10px", marginTop: "20px" }}>
              {checkResult}
            </pre>
          )}
        </>
      )}
    </div>
  );
};

const btnStyle = {
  padding: "10px 16px",
  background: "#4285F4",
  color: "white",
  border: "none",
  borderRadius: "4px",
  cursor: "pointer",
  fontSize: "16px"
};
