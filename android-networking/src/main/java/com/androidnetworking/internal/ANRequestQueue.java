/*
 *    Copyright (C) 2016 Amit Shekhar
 *    Copyright (C) 2011 The Android Open Source Project
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.androidnetworking.internal;

import android.util.Log;

import com.androidnetworking.common.ANRequest;
import com.androidnetworking.common.Priority;
import com.androidnetworking.core.Core;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by amitshekhar on 22/03/16.
 */
public class ANRequestQueue {

    private final static String TAG = ANRequestQueue.class.getSimpleName();
    private final Set<ANRequest> mCurrentRequests = new HashSet<ANRequest>();
    private AtomicInteger mSequenceGenerator = new AtomicInteger();
    private static ANRequestQueue sInstance = null;
    private String userAgent = null;

    public static void initialize() {
        getInstance();
    }

    public static ANRequestQueue getInstance() {
        if (sInstance == null) {
            synchronized (ANRequestQueue.class) {
                sInstance = new ANRequestQueue();
            }
        }
        return sInstance;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public interface RequestFilter {
        boolean apply(ANRequest request);
    }


    private void cancel(RequestFilter filter) {
        synchronized (mCurrentRequests) {
            try {
                for (Iterator<ANRequest> iterator = mCurrentRequests.iterator(); iterator.hasNext(); ) {
                    ANRequest request = iterator.next();
                    if (filter.apply(request)) {
                        request.cancel();
                        if (request.isCanceled()) {
                            request.destroy();
                            iterator.remove();
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void cancelRequestWithGivenTag(final Object tag) {
        try {
            if (tag == null) {
                return;
            }
            cancel(new RequestFilter() {
                @Override
                public boolean apply(ANRequest request) {
                    return request.getTag() == tag;
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int getSequenceNumber() {
        return mSequenceGenerator.incrementAndGet();
    }

    public ANRequest addRequest(ANRequest request) {
        synchronized (mCurrentRequests) {
            try {
                mCurrentRequests.add(request);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        try {
            if (userAgent != null && request.getUserAgent() == null) {
                request.setUserAgent(userAgent);
            }
            request.setSequenceNumber(getSequenceNumber());
            if (request.getPriority() == Priority.IMMEDIATE) {
                request.setFuture(Core.getInstance().getExecutorSupplier().forImmediateNetworkTasks().submit(new InternalRunnable(request)));
            } else {
                request.setFuture(Core.getInstance().getExecutorSupplier().forNetworkTasks().submit(new InternalRunnable(request)));
            }
            Log.d(TAG, "addRequest: after addition - mCurrentRequests size: " + mCurrentRequests.size());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return request;
    }

    public void finish(ANRequest request) {
        synchronized (mCurrentRequests) {
            try {
                mCurrentRequests.remove(request);
                Log.d(TAG, "finish: after removal - mCurrentRequests size: " + mCurrentRequests.size());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
