/*
 * MIT License
 *
 * Copyright (c) 2018 EPAM Systems
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.epam.catgenome.manager.bam;


import com.epam.catgenome.common.AbstractSecurityTest;
import com.epam.catgenome.controller.vo.registration.IndexedFileRegistrationRequest;
import com.epam.catgenome.controller.vo.registration.ReferenceRegistrationRequest;
import com.epam.catgenome.dao.BiologicalDataItemDao;
import com.epam.catgenome.entity.BiologicalDataItemResourceType;
import com.epam.catgenome.entity.bam.*;
import com.epam.catgenome.entity.reference.Chromosome;
import com.epam.catgenome.entity.reference.Reference;
import com.epam.catgenome.manager.reference.ReferenceManager;
import com.epam.catgenome.security.acl.AclPermission;
import com.epam.catgenome.util.AclTestDao;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.util.List;

@RunWith(SpringJUnit4ClassRunner.class)
public class BamSecurityServiceTest extends AbstractSecurityTest {

    private static final String TEST_USER = "TEST_ADMIN";
    private static final String TEST_USER_2 = "TEST_USER";
    private static final String TEST_NSAME = "BIG " + BamSecurityServiceTest.class.getSimpleName();
    private static final String TEST_REF_NAME = "//dm606.X.fa";
    private static final String TEST_BAM_NAME = "//agnX1.09-28.trim.dm606.realign.bam";
    private static final String BAI_EXTENSION = ".bai";

    @Autowired
    private ApplicationContext context;

    @Autowired
    private ReferenceManager referenceManager;

    @Autowired
    private BiologicalDataItemDao biologicalDataItemDao;

    @Autowired
    private BamSecurityService bamSecurityService;

    @Autowired
    private BamFileManager bamFileManager;

    @Autowired
    private AclTestDao aclTestDao;


    private Resource resource;
    private Reference testReference;

    @Before
    public void setup() throws IOException {
        testReference = registerReference(TEST_REF_NAME + biologicalDataItemDao.createBioItemId());
    }

    private Reference registerReference(String name) throws IOException {
        resource = context.getResource("classpath:templates");
        File fastaFile = new File(resource.getFile().getAbsolutePath() + TEST_REF_NAME);

        ReferenceRegistrationRequest request = new ReferenceRegistrationRequest();
        request.setName(name);
        request.setPath(fastaFile.getPath());

        Reference reference = referenceManager.registerGenome(request);
        List<Chromosome> chromosomeList = reference.getChromosomes();
        for (Chromosome chromosome : chromosomeList) {
            String chromosomeName = "X";
            if (chromosome.getName().equals(chromosomeName)) {
                break;
            }
        }
        return reference;
    }

    @Test
    @WithMockUser(username = TEST_USER, roles = "BAM_MANAGER")
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void saveBamTest() throws IOException {
        final String path = resource.getFile().getAbsolutePath() + TEST_BAM_NAME;
        IndexedFileRegistrationRequest request = new IndexedFileRegistrationRequest();
        request.setPath(path);
        request.setIndexPath(path + BAI_EXTENSION);
        request.setName(TEST_NSAME);
        request.setReferenceId(testReference.getId());
        request.setType(BiologicalDataItemResourceType.FILE);

        BamFile bamFile = bamSecurityService.registerBam(request);
        Assert.assertNotNull(bamFile);
        final BamFile loadBamFile = bamFileManager.load(bamFile.getId());
        Assert.assertNotNull(loadBamFile);
        Assert.assertTrue(bamFile.getId().equals(loadBamFile.getId()));
        Assert.assertTrue(bamFile.getName().equals(loadBamFile.getName()));
        Assert.assertTrue(bamFile.getCreatedDate().equals(loadBamFile.getCreatedDate()));
        Assert.assertTrue(bamFile.getReferenceId().equals(loadBamFile.getReferenceId()));
        Assert.assertTrue(bamFile.getPath().equals(loadBamFile.getPath()));
        Assert.assertTrue(bamFile.getIndex().getPath().equals(loadBamFile.getIndex().getPath()));
        Assert.assertTrue(bamFile.getOwner().equals(TEST_USER));

    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(username = TEST_USER_2, roles = "USER")
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void saveBamDoesntPermittedTest() throws IOException {
        final String path = resource.getFile().getAbsolutePath() + TEST_BAM_NAME;
        IndexedFileRegistrationRequest request = new IndexedFileRegistrationRequest();
        request.setPath(path);
        request.setIndexPath(path + BAI_EXTENSION);
        request.setName(TEST_NSAME);
        request.setReferenceId(testReference.getId());
        request.setType(BiologicalDataItemResourceType.FILE);

        bamSecurityService.registerBam(request);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(username = TEST_USER, roles = "U")
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void loadByReferenceTest() throws IOException {
        Reference reference = registerReference(TEST_REF_NAME + biologicalDataItemDao.createBioItemId());

        final String path = resource.getFile().getAbsolutePath() + TEST_BAM_NAME;
        IndexedFileRegistrationRequest request = new IndexedFileRegistrationRequest();
        request.setPath(path);
        request.setIndexPath(path + BAI_EXTENSION);
        request.setName(TEST_NSAME);
        request.setReferenceId(testReference.getId());
        request.setType(BiologicalDataItemResourceType.FILE);

        bamSecurityService.registerBam(request);

        // Create SID for "test" user
        AclTestDao.AclSid testUserSid = new AclTestDao.AclSid(true, TEST_USER);
        testUserSid.setId(1L);
        aclTestDao.createAclSid(testUserSid);

        AclTestDao.AclClass registryAclClass = new AclTestDao.AclClass(Reference.class.getCanonicalName());
        registryAclClass.setId(1L);
        aclTestDao.createAclClassIfNotPresent(registryAclClass);

        AclTestDao.AclObjectIdentity refIdentity = new AclTestDao.AclObjectIdentity(testUserSid, reference.getId(),
                registryAclClass.getId(), null, true);
        refIdentity.setId(1L);
        aclTestDao.createObjectIdentity(refIdentity);

        AclTestDao.AclEntry refAclEntry = new AclTestDao.AclEntry(refIdentity, 1, testUserSid,
                AclPermission.NO_READ.getMask(), true);
        refAclEntry.setId(1L);
        aclTestDao.createAclEntry(refAclEntry);

        bamSecurityService.loadBamFilesByReferenceId(testReference.getId());

    }

}
